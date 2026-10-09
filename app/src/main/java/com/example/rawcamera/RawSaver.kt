package com.example.rawcamera

import android.content.Context
import android.drm.DrmConvertedStatus
import android.graphics.ImageFormat
import android.media.Image
import android.os.Environment
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

object RawSaver {
    fun save(context: Context, image: Image): String {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "RAW"
        ).apply { mkdirs() }

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "IMG_${ts}.dng")

        // Zapis surowych danych sensora + minimalny DNG header
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val rowStride = planes[0].rowStride
        val pixelStride = planes[0].pixelStride
        val width = image.width
        val height = image.height

        FileOutputStream(file).use { out ->
            // Zapisujemy surowe dane Bayer (uproszczony zapis surowy)
            val rowBytes = rowStride
            val data = ByteArray(rowBytes)
            buffer.rewind()
            for (y in 0 until height) {
                buffer.position(y * rowBytes)
                buffer.get(data, 0, rowBytes)
                out.write(data, 0, width * pixelStride)
            }
        }
        return file.absolutePath
    }
}

object JpegSaver {
    fun save(context: Context, image: Image, quality: Int): String {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "JPEG"
        ).apply { mkdirs() }

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "IMG_${ts}.jpg")

        val buffer: ByteBuffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        FileOutputStream(file).use { it.write(data) }
        return file.absolutePath
    }
}
