package org.matrix.chromext.utils

import android.app.Activity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.ScriptDbManager

class XMLHttpRequest(id: String, request: JSONObject, uuid: Double) {
  val id = id
  val uuid = uuid
  val request = request
  var connection: HttpURLConnection? = null

  val url = URL(request.optString("url"))
  val method = request.optString("method")
  val headers = request.optJSONObject("headers")
  val followRedirects = request.optString("redirect") != "error"
  // val binary = request.optBoolean("binary")
  // Not sure where binary data is received
  val nocache = request.optBoolean("nocache")
  val timeout = request.optInt("timeout")

  fun abort() {
    connection?.disconnect()
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
        val encoding = Base64.getEncoder().encodeToString(("${user}:${password}").toByteArray())
        setRequestProperty("Authorization", "Basic " + encoding)
      }
      runCatching {
            if (method == "POST" && request.has("data")) {
              val data = request.optString("data")
              outputStream.write(data.toByteArray())
            }
            val res = inputStream.bufferedReader().use { it.readText() }
            val data =
                JSONObject(
                    mapOf(
                        "status" to getResponseCode(),
                        "statusText" to getResponseMessage(),
                        "responseText" to res))
            data.put(
                "responseHeaders",
                JSONObject(
                    getHeaderFields()
                        .filter { it.key != null }
                        .mapValues { it.value.joinToString(" ") }))
            response("load", data.toString())
          }
          .onFailure {
            if (it is IOException) {
              val error = errorStream?.bufferedReader()?.use { it.readText() } ?: it.message
              Log.d(error + " when connecting to " + url)
              response("error", "{error: '${error}'}")
            } else {
              Log.ex(it)
            }
          }
      disconnect()
    }
  }

  private fun response(type: String, data: String) {
    (Chrome.getContext() as Activity).runOnUiThread {
      val code =
          "window.dispatchEvent(new CustomEvent('xmlhttpRequest', {detail: {id: '${id}', uuid: ${uuid}, type: '${type}', data: ${data}}}));"
      if (UserScriptHook.isInit) {
        UserScriptProxy.loadUrl("javascript: ${code}")
      } else if (WebViewHook.isInit) {
        WebViewHook.evaluateJavascript(code)
      }
      ScriptDbManager.xmlhttpRequests.remove(uuid)
    }
  }
}
