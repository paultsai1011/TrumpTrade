package com.paul.trumptrade

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, val body: String) : Exception("HTTP $code ${body.take(200)}")

object Net {
    const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeout: Int = 15000): String =
        request("GET", url, null, headers, timeout)

    fun post(url: String, json: String, headers: Map<String, String> = emptyMap(), timeout: Int = 90000): String =
        request("POST", url, json, headers + ("Content-Type" to "application/json; charset=utf-8"), timeout)

    private fun request(
        method: String, url: String, body: String?, headers: Map<String, String>, timeout: Int
    ): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeout
            c.readTimeout = timeout
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9,zh-TW;q=0.8")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpException(code, text)
            return text
        } finally {
            c.disconnect()
        }
    }
}
