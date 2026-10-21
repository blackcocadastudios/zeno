package com.blackcocadastudios.zeno.web

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf

/**
 * Small screenshots of tabs for the tab grid. Memory only: nothing about the
 * pages you visit is written to disk for this.
 */
class ThumbnailStore(maxBytes: Int = 24 * 1024 * 1024) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (newValue == null) versions.remove(key)
        }
    }

    /** Bumps when a thumbnail changes, so composables reading it recompose. */
    val versions = mutableStateMapOf<String, Int>()

    fun get(tabId: String): Bitmap? = cache.get(tabId)

    fun put(tabId: String, bitmap: Bitmap) {
        val scaled = scale(bitmap)
        cache.put(tabId, scaled)
        versions[tabId] = (versions[tabId] ?: 0) + 1
    }

    fun remove(tabId: String) {
        cache.remove(tabId)
        versions.remove(tabId)
    }

    fun retain(tabIds: Set<String>) {
        cache.snapshot().keys.filter { it !in tabIds }.forEach(::remove)
    }

    fun clear() {
        cache.evictAll()
        versions.clear()
    }

    /** Thumbnails are drawn small; keep them around 480px wide. */
    private fun scale(source: Bitmap): Bitmap {
        val targetWidth = 480
        if (source.width <= targetWidth) return source
        val height = (source.height * targetWidth.toFloat() / source.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, height, true).also {
            if (it != source) source.recycle()
        }
    }
}
