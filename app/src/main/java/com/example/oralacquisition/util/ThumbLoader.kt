package com.example.oralacquisition.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ThumbLoader {

    private const val CACHE_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun load(context: Context, imageView: ImageView, uri: Uri?, requestSize: Int) {
        imageView.setImageDrawable(null)
        if (uri == null) return
        val key = uri.toString()
        cache.get(key)?.let {
            imageView.setImageBitmap(it)
            return
        }
        executor.execute {
            val bitmap = decode(context, uri, requestSize)
            if (bitmap != null) {
                cache.put(key, bitmap)
                imageView.post { imageView.setImageBitmap(bitmap) }
            }
        }
    }

    private fun decode(context: Context, uri: Uri, requestSize: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    uri, android.util.Size(requestSize, requestSize), null
                )
            } else {
                decodeLegacy(context, uri, requestSize)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeLegacy(context: Context, uri: Uri, requestSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        val sample = calculateInSampleSize(
            bounds.outWidth, bounds.outHeight, requestSize
        )
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, requestSize: Int): Int {
        var sample = 1
        if (height > requestSize || width > requestSize) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / sample) >= requestSize && (halfWidth / sample) >= requestSize) {
                sample *= 2
            }
        }
        return sample
    }
}