package org.matrix.chromext.extension

import java.io.File
import java.io.FileReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import org.json.JSONArray
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

  private fun serveFiles(id: String, connection: Socket) {
    val path = directory.toString() + "/" + id
    val background = extensions.get(id)?.optJSONObject("background")?.optString("page")
    runCatching {
          connection.inputStream.bufferedReader().use {
            val requestLine = it.readLine()
            if (requestLine == null) {
              connection.close()
              return
            }
            val request = requestLine.split(" ")
            if (request[0] == "GET" && request[2] == "HTTP/1.1") {
              val name = request[1]
              val file = File(path + name)
              if (!file.exists() && name != "/ChromeXt.js") {
                connection.outputStream.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
              } else if (file.isDirectory() || name.contains("..")) {
                connection.outputStream.write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray())
              } else {
                val data =
                    if (name == "/" + background) {
                      val html = FileReader(file).use { it.readText() }
                      "<html><head><script>const extension = ${extensions.get(id)!!.put("html", html)}</script><script src='/ChromeXt.js' type='module'></script></head></html>"
                          .toByteArray()
                    } else if (name == "/ChromeXt.js") {
                      script.toByteArray()
                    } else {
                      file.readBytes()
                    }
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
              }
              connection.close()
            }
          }
        }
        .onFailure { Log.ex(it) }
  }

  private fun startServer(id: String) {
    if (extensions.containsKey(id) && !extensions.get(id)!!.has("port")) {
      val server = ServerSocket()
      server.bind(null)
      val port = server.getLocalPort()
      Log.d("Listening at port ${port} for ${id}")
      Chrome.IO.submit {
        runCatching {
              while (true) {
                val socket = server.accept()
                Chrome.IO.submit { serveFiles(id, socket) }
              }
            }
            .onFailure {
              Log.ex(it)
              server.close()
              if (extensions.get(id)?.optInt("port") == port) {
                extensions.get(id)!!.remove("port")
              }
            }
      }
      extensions.get(id)!!.put("port", server.getLocalPort())
      extensions.get(id)!!.put("tabUrl", Chrome.getUrl())
    }
  }

  fun start(): JSONObject {
    extensions.keys.forEach { startServer(it) }
    val info =
        if (extensions.keys.size == 0) {
          Log.d("No extensions found")
          JSONArray()
        } else {
          JSONArray(extensions.map { it.value.put("id", it.key) })
        }
    return JSONObject(mapOf("type" to "init", "manifests" to info))
  }
}
