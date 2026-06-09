package com.example.domingo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun convertirUriABase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            val escalaMax = 480
            val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val bitmapReducido = Bitmap.createScaledBitmap(bitmap, escalaMax,
                (escalaMax * ratio).toInt(), false)

            val outputStream = ByteArrayOutputStream()
            bitmapReducido.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)

            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }
}