package com.kbyai.facerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kbyai.facerecognition.SettingsActivity.Companion.getCameraLens
import com.kbyai.facerecognition.SettingsActivity.Companion.getIdentifyThreshold
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessLevel
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessThreshold
import com.kbyai.facesdk.FaceDetectionParam
import com.kbyai.facesdk.FaceSDK
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@ExperimentalGetImage class CameraActivity : AppCompatActivity() {
    private var cameraExecutorService: ExecutorService? = null
    private var viewFinder: PreviewView? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var faceView: FaceView? = null

    private var context: Context? = null

    private var recognized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        context = this

        viewFinder = findViewById(R.id.preview)
        faceView = findViewById(R.id.faceView)
        cameraExecutorService = Executors.newFixedThreadPool(1)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            viewFinder?.post( {
                setUpCamera()
            })
        }
    }

    public override fun onResume() {
        super.onResume()

        recognized = false
    }

    public override fun onPause() {
        super.onPause()

        faceView!!.setFaceBoxes(null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                viewFinder!!.post {
                    setUpCamera()
                }
            }
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this@CameraActivity)
        cameraProviderFuture.addListener({
            // CameraProvider
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: ExecutionException) {
            } catch (e: InterruptedException) {
            }

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this@CameraActivity))
    }

    @SuppressLint("RestrictedApi", "UnsafeExperimentalUsageError")
    private fun bindCameraUseCases() {
        val rotation = viewFinder!!.display.rotation

        cameraSelector = CameraSelector.Builder().requireLensFacing(
            getCameraLens(
                this
            )
        ).build()

        preview = Preview.Builder()
            .setTargetResolution(Size(PREVIEW_WIDTH, PREVIEW_HEIGHT))
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(
                Size(
                    PREVIEW_WIDTH,
                    PREVIEW_HEIGHT
                )
            ) // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer!!.setAnalyzer(cameraExecutorService!!, FaceAnalyzer())

        cameraProvider!!.unbindAll()

        try {
            camera = cameraProvider!!.bindToLifecycle(
                this, cameraSelector!!, preview, imageAnalyzer
            )

            preview!!.setSurfaceProvider(viewFinder!!.surfaceProvider)
        } catch (exc: Exception) {
        }
    }

    internal inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            analyzeImage(imageProxy)
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (recognized == true) {
            imageProxy.close()
            return
        }

        try {
            val image = imageProxy.image

            val planes = image!!.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer[nv21, 0, ySize]
            vBuffer[nv21, ySize, vSize]
            uBuffer[nv21, ySize + vSize, uSize]

            var cameraMode = 7
            if (getCameraLens(context!!) == CameraSelector.LENS_FACING_BACK) {
                cameraMode = 6
            }
            val bitmap = FaceSDK.yuv2Bitmap(nv21, image.width, image.height, cameraMode)

            val faceDetectionParam = FaceDetectionParam()
            faceDetectionParam.check_liveness = true
            faceDetectionParam.check_liveness_level = getLivenessLevel(this)
            val faceBoxes = FaceSDK.faceDetection(bitmap, faceDetectionParam)

            runOnUiThread {
                faceView!!.setFrameSize(Size(bitmap.width, bitmap.height))
                faceView!!.setFaceBoxes(faceBoxes)
            }

            if (faceBoxes.size > 0) {
                val faceBox = faceBoxes[0]
                if (faceBox.liveness > getLivenessThreshold(context!!)) {
                    val templates = FaceSDK.templateExtraction(bitmap, faceBox)

                    var maxSimiarlity = 0f
                    var maximiarlityPerson: Person? = null
                    for (person in DBManager.personList) {
                        val similarity = FaceSDK.similarityCalculation(templates, person.templates)
                        if (similarity > maxSimiarlity) {
                            maxSimiarlity = similarity
                            maximiarlityPerson = person
                        }
                    }

                    if (maxSimiarlity > getIdentifyThreshold(this)) {
                        recognized = true
                        val identifiedPerson = maximiarlityPerson
                        val identifiedSimilarity = maxSimiarlity

                        runOnUiThread {
                            val faceImage = Utils.cropFace(bitmap, faceBox)
                            val intent = Intent(context, ResultActivity::class.java)
                            intent.putExtra("identified_face", faceImage)
                            intent.putExtra("enrolled_face", identifiedPerson!!.face)
                            intent.putExtra("identified_name", identifiedPerson.name)
                            intent.putExtra("similarity", identifiedSimilarity)
                            intent.putExtra("liveness", faceBox.liveness)
                            intent.putExtra("yaw", faceBox.yaw)
                            intent.putExtra("roll", faceBox.roll)
                            intent.putExtra("pitch", faceBox.pitch)
                            startActivity(intent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    companion object {
        var TAG: String = CameraActivity::class.java.simpleName
        var PREVIEW_WIDTH: Int = 720
        var PREVIEW_HEIGHT: Int = 1280
    }
}