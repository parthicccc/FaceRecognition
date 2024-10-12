package com.kbyai.facerecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import com.kbyai.facesdk.FaceBox
import java.io.IOException

object Utils {
    fun cropFace(src: Bitmap, faceBox: FaceBox): Bitmap {
        val centerX = (faceBox.x1 + faceBox.x2) / 2
        val centerY = (faceBox.y1 + faceBox.y2) / 2
        val cropWidth = ((faceBox.x2 - faceBox.x1) * 1.4f).toInt()

        var cropX1 = centerX - cropWidth / 2
        var cropY1 = centerY - cropWidth / 2
        var cropX2 = centerX + cropWidth / 2
        var cropY2 = centerY + cropWidth / 2
        if (cropX1 < 0) cropX1 = 0
        if (cropX2 >= src.width) cropX2 = src.width - 1
        if (cropY1 < 0) cropY1 = 0
        if (cropY2 >= src.height) cropY2 = src.height - 1


        val cropScaleWidth = 200
        val cropScaleHeight = 200
        val scaleWidth = (cropScaleWidth.toFloat()) / (cropX2 - cropX1 + 1)
        val scaleHeight = (cropScaleHeight.toFloat()) / (cropY2 - cropY1 + 1)

        val m = Matrix()

        m.setScale(1.0f, 1.0f)
        m.postScale(scaleWidth, scaleHeight)
        val cropped = Bitmap.createBitmap(
            src, cropX1, cropY1, (cropX2 - cropX1 + 1), (cropY2 - cropY1 + 1), m,
            true /* filter */
        )
        return cropped
    }

    fun getOrientation(context: Context, photoUri: Uri?): Int {
        val cursor = context.contentResolver.query(
            photoUri!!,
            arrayOf(MediaStore.Images.ImageColumns.ORIENTATION), null, null, null
        )

        if (cursor!!.count != 1) {
            return -1
        }

        cursor.moveToFirst()
        return cursor.getInt(0)
    }

    @Throws(IOException::class)
    fun getCorrectlyOrientedImage(context: Context, photoUri: Uri?): Bitmap {
        var `is` = context.contentResolver.openInputStream(photoUri!!)
        val dbo = BitmapFactory.Options()
        dbo.inJustDecodeBounds = true
        BitmapFactory.decodeStream(`is`, null, dbo)
        `is`!!.close()

        val orientation = getOrientation(context, photoUri)

        var srcBitmap: Bitmap
        `is` = context.contentResolver.openInputStream(photoUri)
        srcBitmap = BitmapFactory.decodeStream(`is`)
        `is`!!.close()

        if (orientation > 0) {
            val matrix = Matrix()
            matrix.postRotate(orientation.toFloat())

            srcBitmap = Bitmap.createBitmap(
                srcBitmap, 0, 0, srcBitmap.width,
                srcBitmap.height, matrix, true
            )
        }

        return srcBitmap
    }
}
