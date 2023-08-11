package org.matrix.chromext.utils

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
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
        val user = request.optInt("user")
        val password = request.optInt("password")
        val encoding = Base64.encodeToString(("${user}:${password}").toByteArray(), Base64.DEFAULT)
        setRequestProperty("Authorization", "Basic " + encoding)
      }
      runCatching {
            response("loadstart", disconnect = false)
            if (method == "POST" && request.has("data")) {
              val data = request.optString("data")
              if (binary) {
                outputStream.write(Base64.decode(data, Base64.DEFAULT))
              } else {
                outputStream.write(data.toByteArray())
              }
            }
            val data = JSONObject(mapOf("status" to responseCode, "statusText" to responseMessage))
            data.put(
                "responseHeaders",
                JSONObject(
                    headerFields
                        .filter { it.key != null }
                        .mapValues { it.value.joinToString(" ") }))

            val res =
                if (responseType !in listOf("", "text", "document", "json")) {
                  Base64.encodeToString(inputStream.readAllBytes(), Base64.DEFAULT)
                } else {
                  inputStream.bufferedReader().use { it.readText() }
                }

            data.put("response", res)

            response("load", data, false)
            // data.remove("response")
            // response("loadend", "{}")
          }
          .onFailure {
            if (it is IOException) {
              val error = errorStream?.bufferedReader()?.use { it.readText() } ?: it.message
              Log.d(it.toString() + ". ${error} when connecting to ${url}")
              if (it is SocketTimeoutException) {
                response("timeout")
              } else {
                response("error", JSONObject(mapOf("error" to error)))
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
