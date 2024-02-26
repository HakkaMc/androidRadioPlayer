package com.example.androidradioplayer

import android.media.MediaDataSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UrlStreamDataSource(url: String?) : MediaDataSource() {
    private val url: URL
    private var connection: HttpURLConnection? = null

    init {
        this.url = URL(url)
    }

    @get:Throws(IOException::class)
    val contentType: String?
        get() = if (connection != null) {
            connection!!.contentType
        } else null

    @Throws(IOException::class)
    override fun getSize(): Long {
        return if (connection != null) {
            connection!!.contentLength.toLong()
        } else -1
    }

    @Throws(IOException::class)
    override fun close() {
        if (connection != null) {
            connection!!.disconnect()
        }
    }

    @Throws(IOException::class)
    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int
    ): Int {
        if (connection == null) {
            connection = url.openConnection() as HttpURLConnection
            connection!!.requestMethod = "GET"
            connection!!.connect()
        }
        val inputStream = connection!!.inputStream
        return inputStream.read(buffer, offset, size)
    }

    @Throws(IOException::class)
    fun setReadRequestLength(length: Int) {
        if (connection != null) {
            connection!!.setFixedLengthStreamingMode(length)
        }
    }
}