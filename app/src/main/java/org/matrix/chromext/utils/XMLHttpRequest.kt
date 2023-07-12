package org.matrix.chromext.utils

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener

class XMLHttpRequest(id: String, request: JSONObject, uuid: Double) {
  val id = id
  val uuid = uuid
  val request = request
  var connection: HttpURLConnection? = null

  val url = URL(request.optString("url"))
  val method = request.optString("method")
  val headers = request.optJSONObject("headers")
  val followRedirects = request.optString("redirect") != "error"
  val binary = request.optBoolean("binary")
  val nocache = request.optBoolean("nocache")
  val timeout = request.optInt("timeout")

  var codes = mutableListOf<String>()

  fun abort() {
    response("abort", "{abort: 'Abort on request'}")
  }

  fun send() {
    connection = url.openConnection() as HttpURLConnection
    with(connection!!) {
      setRequestMethod(method)
      headers?.keys()?.forEach { setRequestProperty(it, headers.optString(it)) }
      setInstanceFollowRedirects(followRedirects)
      setUseCaches(!nocache)
      setConnectTimeout(timeout)
      if (request.has("cookie")) {
        setRequestProperty("Cookie", request.optString("cookie"))
      }
      if (request.has("user")) {
        val user = request.optInt("user")
        val password = request.optInt("password")
        val encoding = Base64.encodeToString(("${user}:${password}").toByteArray(), Base64.DEFAULT)
        setRequestProperty("Authorization", "Basic " + encoding)
      }
      runCatching {
            response("loadstart", "{}", false, false)
            if (method == "POST" && request.has("data")) {
              val data = request.optString("data")
              if (binary) {
                outputStream.write(Base64.decode(data, Base64.DEFAULT))
              } else {
                outputStream.write(data.toByteArray())
              }
            }
            val data =
                JSONObject(
                    mapOf("status" to getResponseCode(), "statusText" to getResponseMessage()))
            data.put(
                "responseHeaders",
                JSONObject(
                    getHeaderFields()
                        .filter { it.key != null }
                        .mapValues { it.value.joinToString(" ") }))

            val bufferSize = DEFAULT_BUFFER_SIZE * 10
            var bytesRead: Long = 0
            val buffer = ByteArray(bufferSize)
            var bytes = inputStream.read(buffer)

            while (bytes >= 0) {
              val readByte =
                  if (bytes == bufferSize) {
                    buffer
                  } else {
                    buffer.dropLast(bufferSize - bytes).toByteArray()
                  }
              val res = Base64.encodeToString(readByte, Base64.DEFAULT)
              bytesRead += bytes
              data.put("loaded", bytesRead)
              data.put("response", res)
              response("progress", data.toString(), false, true)
              bytes = inputStream.read(buffer)
            }

            data.remove("response")
            response("load", data.toString())
          }
          .onFailure {
            if (it is IOException) {
              val error = errorStream?.bufferedReader()?.use { it.readText() } ?: it.message
              Log.d(it.toString() + ". ${error} when connecting to ${url}")
              if (it is SocketTimeoutException) {
                response("timeout", "{}")
              } else {
                response("error", JSONObject(mapOf("error" to error)).toString())
              }
            } else {
              Log.ex(it)
            }
          }
      response("loadend", "{}")
    }
  }

  private fun response(
      type: String,
      data: String,
      disconnect: Boolean = true,
      caching: Boolean = true
  ) {
    codes.add(
        "window.dispatchEvent(new CustomEvent('xmlhttpRequest', {detail: {id: '${id}', uuid: ${uuid}, type: '${type}', data: ${data}}}));")
    if (disconnect || !caching) {
      Chrome.evaluateJavascript(codes.toList())
      codes.clear()
    }
    if (disconnect) {
      Listener.xmlhttpRequests.remove(uuid)
      connection?.disconnect()
    }
  }
}
