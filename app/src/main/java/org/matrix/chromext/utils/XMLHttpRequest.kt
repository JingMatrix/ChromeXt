package org.matrix.chromext.utils

import android.util.Base64
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
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
  val anonymous = request.optBoolean("anonymous")
  val nocache = request.optBoolean("nocache")
  val timeout = request.optInt("timeout")
  val buffersize = request.optInt("buffersize", 8)
  val responseType = request.optString("responseType")

  init {
    if (UserScriptHook.isInit) {
      val manager = CookieHandler.getDefault() as CookieManager
      if (anonymous || request.has("cookie")) {
        if (anonymous) manager.setCookiePolicy(CookiePolicy.ACCEPT_NONE)
        val uri = url.toURI()
        val cookieStore = manager.getCookieStore()
        cookieStore.get(uri).forEach { cookieStore.remove(uri, it) }
      } else {
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
      }
    }
  }

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
      if (request.has("cookie") && !anonymous)
          setRequestProperty("Cookie", request.optString("cookie"))
      if (WebViewHook.isInit && !anonymous) {
        val manger = android.webkit.CookieManager.getInstance()
        addRequestProperty("Cookie", manger.getCookie(url.toString()))
      }

      if (request.has("user")) {
        val user = request.optString("user")
        val password = request.optString("password")
        val encoded = Base64.encodeToString(("${user}:${password}").toByteArray(), Base64.DEFAULT)
        setRequestProperty("Authorization", "Basic " + encoded)
      }

      var data = JSONObject()
      runCatching {
            if (method != "GET" && request.has("data")) {
              val input = request.optString("data")
              if (binary) {
                outputStream.write(Base64.decode(input, Base64.DEFAULT))
              } else {
                outputStream.write(input.toByteArray())
              }
            }

            data.put("status", responseCode)
            data.put("statusText", responseMessage)
            val headers = headerFields.filter { it.key != null }.mapValues { JSONArray(it.value) }
            data.put("headers", JSONObject(headers))
            val binary =
                responseType !in listOf("", "text", "document", "json") ||
                    headers.containsKey("Content-Encoding") ||
                    (headers.get("Content-Type")?.optString(0, "")?.contains("charset") == true)
            data.put("binary", binary)
            if (WebViewHook.isInit && !anonymous) {
              val manger = android.webkit.CookieManager.getInstance()
              headerFields
                  .filter { it.key != null && it.key.startsWith("Set-Cookie") }
                  .forEach { it.value.forEach { manger.setCookie(url.toString(), it) } }
            }

            val buffer = ByteArray(buffersize * DEFAULT_BUFFER_SIZE)
            while (true) {
              var bytes = 0
              while (buffer.size > bytes) {
                val b = inputStream.read(buffer, bytes, buffer.size - bytes)
                if (b == 0 || b == -1) break
                bytes += b
              }
              if (bytes == 0) break
              val chunk =
                  if (binary) {
                    Base64.encodeToString(buffer, 0, bytes, Base64.DEFAULT)
                  } else {
                    String(buffer, 0, bytes)
                  }
              data.put("chunk", chunk)
              data.put("bytes", bytes)
              response("progress", data, false)
              data.remove("headers")
            }
            response("load", data, false)
          }
          .onFailure {
            if (it is IOException) {
              data.put("type", it::class.java.name)
              data.put("message", it.message)
              errorStream?.bufferedReader()?.use { it.readText() }?.let { data.put("error", it) }
              // Log.d("XMLHttpRequest failed with ${url}: " + it.toString())
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
    val code = "Symbol.${Local.name}.unlock(${Local.key}).post('xmlhttpRequest', ${response});"
    Chrome.evaluateJavascript(listOf(code), currentTab)
    if (disconnect) {
      Listener.xmlhttpRequests.remove(response.getDouble("uuid"))
      connection?.disconnect()
    }
  }
}
