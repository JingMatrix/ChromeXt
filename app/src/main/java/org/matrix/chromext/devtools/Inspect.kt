package org.matrix.chromext.devtools

import android.net.LocalSocket
import org.json.JSONArray

const val DEV_FRONT_END = "https://chrome-devtools-frontend.appspot.com"

fun getInspectPages(): JSONArray? {
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
  return JSONArray(response)
}

fun hitDevTools(): LocalSocket {
  val client = LocalSocket()
  connectDevTools(client)
  client.outputStream.write("GET /json HTTP/1.1\r\n\r\n".toByteArray())
  return client
}
