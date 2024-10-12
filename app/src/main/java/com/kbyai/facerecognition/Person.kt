package com.kbyai.facerecognition

import android.graphics.Bitmap

class Person {
    @JvmField
    var name: String? = null
    @JvmField
    var face: Bitmap? = null
    lateinit var templates: ByteArray

    constructor()

    constructor(name: String?, face: Bitmap?, templates: ByteArray) {
        this.name = name
        this.face = face
        this.templates = templates
    }
}
