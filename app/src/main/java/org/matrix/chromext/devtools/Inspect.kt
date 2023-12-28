package org.matrix.chromext.devtools

import android.net.LocalSocket
import org.json.JSONArray
import org.matrix.chromext.utils.Log

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
        Log.ex(it)
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

object DevSessions {
  private val clients = mutableSetOf<DevToolClient>()

  fun get(tabId: String): DevToolClient? {
    var cached = clients.find { it.tabId == tabId }
    if (cached?.isClosed() == true) {
      clients.remove(cached)
      cached = null
    }
    return cached
  }

  fun new(tabId: String): DevToolClient {
    var client = get(tabId) ?: DevToolClient(tabId)
    if (client.isClosed()) {
      hitDevTools().close()
      client = DevToolClient(tabId)
    }
    return client
  }

  fun add(client: DevToolClient?) {
    if (client == null) return
    val cached = clients.find { it.tabId == client.tabId }
    if (cached != null) clients.remove(cached)
    clients.add(client)
  }
}
