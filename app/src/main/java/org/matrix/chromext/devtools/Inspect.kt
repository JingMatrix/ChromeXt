package org.matrix.chromext.devtools

import android.net.LocalSocket
import org.json.JSONArray
import org.matrix.chromext.Chrome

const val DEV_FRONT_END = "https://chrome-devtools-frontend.appspot.com"

fun getInspectPages(inspect: Boolean = true): JSONArray? {
  var response = ""
  runCatching {
        hitDevTools().inputStream.bufferedReader().use {
          while (true) {
            val line = it.readLine()
            if (line.length == 0) {
              val bufferSize =
                  response
                      .split("\n")
                      .find { it.startsWith("Content-Length") }!!
                      .substring(15)
                      .toInt()
              val buffer = CharArray(bufferSize)
              it.read(buffer)
              response = buffer.joinToString("")
              it.close()
              break
            }
            response += line + "\n"
          }
        }
      }
      .onFailure {
        return null
      }
  val pages = JSONArray(response)
  if (inspect) {
    val code = "window.dispatchEvent(new CustomEvent('inspect_pages', { detail: ${pages} }));"
    Chrome.evaluateJavascript(listOf(code))
  }
  return JSONArray(response)
}

fun hitDevTools(): LocalSocket {
  val client = LocalSocket()
  connectDevTools(client)
  client.outputStream.write("GET /json HTTP/1.1\r\n\r\n".toByteArray())
  return client
}
