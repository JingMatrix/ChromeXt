package org.matrix.chromext.extension

import java.io.File
import java.io.FileReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import kotlin.concurrent.thread
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

object LocalFiles {

  private val directory: File
  private val extensions = mutableMapOf<String, JSONObject>()
  val script: String

  init {
    val ctx = Chrome.getContext()
    directory = File(ctx.getExternalFilesDir(null), "Extension")
    script = ctx.assets.open("extension.js").bufferedReader().use { it.readText() }
    if (!directory.exists()) directory.mkdirs()
    directory.listFiles()?.forEach {
      val path = it.name
      val manifest = File(it, "manifest.json")
      if (manifest.exists()) {
        val json = FileReader(manifest).use { it.readText() }
        runCatching { extensions.put(path, JSONObject(json)) }
      }
    }
  }

  private fun serveFiles(path: String, connection: Socket) {
    runCatching {
          Log.d(path + "is requested")
          val request =
              String(connection.inputStream.readNBytes(DEFAULT_BUFFER_SIZE / 16))
                  .split("\r\n")[0]
                  .split(" ")
          if (request[0] == "GET" && request[2] == "HTTP/1.1") {
            val name = request[1]
            val file = File(path + name)
            if (file.exists()) {
              val data = file.readBytes()
              val type = URLConnection.guessContentTypeFromName(name) ?: "text/plain"
              val response =
                  arrayOf(
                      "HTTP/1.1 200",
                      "Content-Length: ${data.size}",
                      "Content-Type: ${type}",
                      "Access-Control-Allow-Origin: *")
              connection.outputStream.write(
                  (response.joinToString("\r\n") + "\r\n\r\n").toByteArray())
              connection.outputStream.write(data)
            } else {
              connection.outputStream.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
            }
            connection.close()
          }
        }
        .onFailure { Log.ex(it) }
  }

  private fun startServer(name: String) {
    if (extensions.containsKey(name) && !extensions.get(name)!!.has("port")) {
      val server = ServerSocket()
      server.bind(null)
      thread {
        runCatching {
              while (true) {
                val socket = server.accept()
                serveFiles(directory.toString() + "/" + name, socket)
              }
            }
            .onFailure {
              Log.ex(it)
              server.close()
              if (extensions.get(name)?.optInt("port") == server.getLocalPort()) {
                extensions.get(name)!!.remove("port")
              }
            }
      }
      extensions.get(name)!!.put("port", server.getLocalPort())
    }
  }

  fun start(): JSONObject {
    extensions.keys.forEach { startServer(it) }
    val info = JSONObject(extensions as Map<String, JSONObject>)
    return JSONObject().put("detail", JSONObject(mapOf("type" to "init", "manifest" to info)))
  }
}
