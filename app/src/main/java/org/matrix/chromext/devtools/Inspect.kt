package org.matrix.chromext.devtools

import android.net.LocalSocket
import org.json.JSONArray
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy

const val DEV_FRONT_END = "https://chrome-devtools-frontend.appspot.com"

fun getInspectPages() {
  var pages = ""
  hitDevTools().inputStream.bufferedReader().use {
    while (true) {
      val line = it.readLine()
      if (line.length == 0) {
        val bufferSize =
            pages.split("\n").find { it.startsWith("Content-Length") }!!.substring(15).toInt()
        val buffer = CharArray(bufferSize)
        it.read(buffer)
        pages = buffer.joinToString("")
        it.close()
        break
      }
      pages += line + "\n"
    }
  }

  val code = "window.dispatchEvent(new CustomEvent('inspect_pages',{detail: ${JSONArray(pages)}}));"
  if (UserScriptHook.isInit) {
    UserScriptProxy.evaluateJavascript(code)
  } else if (WebViewHook.isInit) {
    WebViewHook.evaluateJavascript(code)
  }
}

fun hitDevTools(): LocalSocket {
  val client = LocalSocket()
  connectDevTools(client)
  client.outputStream.write("GET /json HTTP/1.1\r\n\r\n".toByteArray())
  return client
}
