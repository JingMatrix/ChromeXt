package org.matrix.chromext.utils

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.script.Local

class XMLHttpRequest(id: String, request: JSONObject, uuid: Double, currentTab: Any?) {
  val response = JSONObject(mapOf("id" to id, "uuid" to uuid))
  val request = request
  val currentTab = currentTab
  var connection: HttpURLConnection? = null

  val url = URL(request.optString("url"))
  val method = request.optString("method")
  val headers = request.optJSONObject("headers")
  val followRedirects = request.optString("redirect") != "error"
  val binary = request.optBoolean("binary")
  val nocache = request.optBoolean("nocache")
  val timeout = request.optInt("timeout")
  val responseType = request.optString("responseType")

  fun abort() {
    response("abort", JSONObject().put("abort", "Abort on request"))
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
        val user = request.optString("user")
        val password = request.optString("password")
        val encoded = Base64.encodeToString(("${user}:${password}").toByteArray(), Base64.DEFAULT)
        setRequestProperty("Authorization", "Basic " + encoded)
      }
      var data = JSONObject()
      runCatching {
            response("loadstart", disconnect = false)
            if (method == "POST" && request.has("data")) {
              val input = request.optString("data")
              if (binary) {
                outputStream.write(Base64.decode(input, Base64.DEFAULT))
              } else {
                outputStream.write(input.toByteArray())
              }
            }
            data = JSONObject(mapOf("status" to responseCode, "statusText" to responseMessage))
            val headers = headerFields.filter { it.key != null }.mapValues { JSONArray(it.value) }
            data.put("headers", JSONObject(headers))

            val res =
                if (responseType in listOf("", "text", "document", "json")) {
                  inputStream.bufferedReader().use { it.readText() }
                } else {
                  Base64.encodeToString(inputStream.readBytes(), Base64.DEFAULT)
                }

            data.put("response", res)

            response("load", data, false)
          }
          .onFailure {
            if (it is IOException) {
              data.put("type", it::class.java.name)
              data.put("message", it.message)
              errorStream?.bufferedReader()?.use { it.readText() }?.let { data.put("error", it) }
              Log.d("XMLHttpRequest failed with ${url}: " + it.toString())
              if (it is SocketTimeoutException) {
                response("timeout", data.put("bytesTransferred", it.bytesTransferred))
              } else {
                response("error", data)
              }
            } else {
              Log.ex(it)
            }
          }
    }
  }

  private fun response(
      type: String,
      data: JSONObject = JSONObject(),
      disconnect: Boolean = true,
  ) {
    response.put("type", type)
    response.put("data", data)
    val code = "ChromeXt.unlock(${Local.key}).post('xmlhttpRequest', ${response});"
    Chrome.evaluateJavascript(listOf(code), currentTab)
    if (disconnect) {
      Listener.xmlhttpRequests.remove(response.getDouble("uuid"))
      connection?.disconnect()
    }
  }
}
