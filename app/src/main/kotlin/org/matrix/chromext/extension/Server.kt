package org.matrix.chromext.extension

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.randomString

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
)

class HttpResponse(
    val status: Int = 200,
    val contentType: String = "text/plain; charset=utf-8",
    val body: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
) {
  companion object {
    fun text(text: String, contentType: String = "text/plain; charset=utf-8") =
        HttpResponse(body = text.toByteArray(), contentType = contentType)

    fun json(json: JSONObject) = text(json.toString(), "application/json; charset=utf-8")

    fun asset(name: String, replacements: Map<String, String> = emptyMap()): HttpResponse {
      val text = runCatching {
        Chrome.getContext().assets.open(name).bufferedReader().use { it.readText() }
      }
          .getOrElse {
            return HttpResponse(404, body = "Missing asset $name".toByteArray())
          }
      var content = text
      replacements.forEach { (key, value) -> content = content.replace(key, value) }
      val type =
          when {
            name.endsWith(".html") -> "text/html; charset=utf-8"
            name.endsWith(".js") -> "text/javascript; charset=utf-8"
            name.endsWith(".css") -> "text/css; charset=utf-8"
            name.endsWith(".json") -> "application/json; charset=utf-8"
            else -> "text/plain; charset=utf-8"
          }
      return text(content, type)
    }

    fun notFound(message: String = "Not Found") = text(message, "text/plain; charset=utf-8")
  }
}

/**
 * Loopback HTTP servers.
 *
 * One server hosts the manager front end, one serves each extension from its own origin so that
 * extensions get isolated storages and can use ES modules.
 */
object ExtServer {

  private const val LOOPBACK = "127.0.0.1"

  private var manager: ServerSocket? = null
  private val servers = mutableMapOf<String, ServerSocket>()
  private val ports = mutableMapOf<String, Int>()

  val token: String = randomString(24)

  /** When the host page last polled, used to notice that it stopped (frozen or killed). */
  @Volatile
  var lastPollAt: Long = 0L
    private set

  fun notePoll() {
    lastPollAt = System.currentTimeMillis()
  }

  /** Per process value appended to the host url: a page carrying another one is stale. */
  val hostToken: String = randomString(10)

  fun hostPath(): String = "http://$LOOPBACK:$managerPort/host.html"

  fun hostUrl(): String = hostPath() + "?p=" + hostToken

  val managerPort: Int
    get() {
      startManager()
      return manager!!.localPort
    }

  fun managerUrl(): String = "http://$LOOPBACK:$managerPort/manager.html"

  /** Open the manager in a new tab of the browser we are injected in. */
  fun openManager() {
    val intent =
        android.content
            .Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(managerUrl()))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .setPackage(Chrome.packageName)
    Chrome.getContext().startActivity(intent)
  }

  /** True for the pages ChromeXt serves itself, where extensions must stay out. */
  fun isOwnPage(url: String?): Boolean =
      url != null && url.startsWith("http://$LOOPBACK:$managerPort/")

  fun portOf(extension: Extension): Int {
    servers[extension.id]?.let {
      return it.localPort
    }
    val server = bind()
    servers[extension.id] = server
    ports[extension.id] = server.localPort
    accept(server) { request -> serveExtension(extension, request) }
    Log.i("Extension ${extension.id} served at http://$LOOPBACK:${server.localPort}/")
    return server.localPort
  }

  fun origin(extension: Extension): String = "http://$LOOPBACK:${portOf(extension)}"

  fun stop(extension: Extension?) {
    if (extension == null) return
    servers.remove(extension.id)?.let { runCatching { it.close() } }
    ports.remove(extension.id)
  }

  fun startManager() {
    if (manager != null) return
    val server = bindHost()
    manager = server
    accept(server) { request -> serveManager(request) }
    Log.i("Extension manager served at http://$LOOPBACK:${server.localPort}/manager.html")
  }

  fun startAll() {
    startManager()
    Extensions.enabled().filter { it.hasBackground }.forEach { portOf(it) }
  }

  /**
   * A stable port for the host page.
   *
   * The host tab is reused across browser restarts, which only works when its url does not change,
   * so a previously used port is preferred and recorded.
   */
  private fun bindHost(): ServerSocket {
    val prefs =
        Chrome.getContext().getSharedPreferences("ChromeXt", android.content.Context.MODE_PRIVATE)
    val saved = prefs.getInt("extensionHostPort", 0)
    val candidates = buildList {
      if (saved != 0) add(saved)
      addAll(15243..15263)
    }
    candidates.forEach { port ->
      runCatching { bindPort(port) }
          .onSuccess {
            if (it.localPort != saved)
                prefs.edit().putInt("extensionHostPort", it.localPort).apply()
            Log.i("Extension host listens on port ${it.localPort}")
            return it
          }
          .onFailure { Log.d("Port $port is taken: ${it.message}") }
    }
    val socket = bind()
    prefs.edit().putInt("extensionHostPort", socket.localPort).apply()
    return socket
  }

  private fun bindPort(port: Int): ServerSocket {
    val socket = ServerSocket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), port))
    return socket
  }

  private fun bind(): ServerSocket {
    val socket = ServerSocket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), 0))
    return socket
  }

  private fun accept(server: ServerSocket, route: (HttpRequest) -> HttpResponse) {
    Chrome.IO.submit {
      while (!server.isClosed) {
        val client = runCatching {
          server.accept()
        }
            .getOrElse {
              if (!server.isClosed) Log.e("Socket accept failed: ${it.message}")
              break
            }
        Chrome.IO.submit { handle(client, route) }
      }
    }
  }

  private fun handle(client: Socket, route: (HttpRequest) -> HttpResponse) {
    try {
      client.soTimeout = 15000
      val input = client.getInputStream()
      val request = readRequest(input) ?: return
      val response = runCatching {
        route(request)
      }
          .getOrElse {
            Log.ex(it, "Fail to serve ${request.path}")
            HttpResponse(500, body = (it.message ?: "error").toByteArray())
          }
      writeResponse(client, request, response)
    } catch (e: Throwable) {
      Log.d("Connection dropped: ${e.message}")
    } finally {
      runCatching { client.close() }
    }
  }

  private fun readRequest(input: InputStream): HttpRequest? {
    val head = ByteArrayOutputStream()
    var matched = 0
    val terminator = byteArrayOf(13, 10, 13, 10)
    while (true) {
      val b = input.read()
      if (b < 0) return null
      head.write(b)
      if (b.toByte() == terminator[matched]) {
        matched++
        if (matched == 4) break
      } else {
        matched = if (b.toByte() == terminator[0]) 1 else 0
      }
      if (head.size() > 65536) return null
    }
    val lines = head.toString("UTF-8").split("\r\n").filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val parts = lines[0].split(" ")
    if (parts.size < 2) return null
    val headers = mutableMapOf<String, String>()
    lines.drop(1).forEach { line ->
      val colon = line.indexOf(':')
      if (colon > 0)
          headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
    }
    val rawPath = parts[1]
    val question = rawPath.indexOf('?')
    val path = if (question < 0) rawPath else rawPath.substring(0, question)
    val query = mutableMapOf<String, String>()
    if (question >= 0) {
      rawPath
          .substring(question + 1)
          .split('&')
          .filter { it.isNotEmpty() }
          .forEach { pair ->
            val eq = pair.indexOf('=')
            val key = if (eq < 0) pair else pair.substring(0, eq)
            val value = if (eq < 0) "" else pair.substring(eq + 1)
            query[java.net.URLDecoder.decode(key, "UTF-8")] =
                java.net.URLDecoder.decode(value, "UTF-8")
          }
    }
    val length = headers["content-length"]?.toIntOrNull() ?: 0
    val body = ByteArray(length)
    var read = 0
    while (read < length) {
      val count = input.read(body, read, length - read)
      if (count < 0) break
      read += count
    }
    return HttpRequest(parts[0].uppercase(), path, query, headers, body)
  }

  private fun writeResponse(client: Socket, request: HttpRequest, response: HttpResponse) {
    val output = client.getOutputStream()
    val reason =
        when (response.status) {
          200 -> "OK"
          400 -> "Bad Request"
          403 -> "Forbidden"
          404 -> "Not Found"
          500 -> "Internal Server Error"
          else -> "OK"
        }
    val headers =
        mutableListOf(
            "HTTP/1.1 ${response.status} $reason",
            "Content-Length: ${response.body.size}",
            "Content-Type: ${response.contentType}",
            "Connection: close",
        )
    headers.addAll(response.headers.map { "${it.key}: ${it.value}" })
    output.write((headers.joinToString("\r\n") + "\r\n\r\n").toByteArray())
    if (request.method != "HEAD") output.write(response.body)
    output.flush()
  }

  // ---- extension origin ------------------------------------------------------

  private fun contentType(name: String): String =
      when (name.substringAfterLast('.', "").lowercase()) {
        "js",
        "mjs" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "html",
        "htm" -> "text/html; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg",
        "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
      }

  private fun serveExtension(extension: Extension, request: HttpRequest): HttpResponse {
    val cors =
        mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Headers" to "*",
            // Extension files are edited in place while developing, never cache them
            "Cache-Control" to "no-store")
    if (request.method == "OPTIONS") return HttpResponse(204, body = ByteArray(0), headers = cors)
    val path = request.path
    val response =
        when {
          path == "/__chromext__.js" || path == "/ChromeXt.js" ->
              HttpResponse.text(ChromeApi.script(extension), "text/javascript; charset=utf-8")
          path == "/" || path.isEmpty() ->
              HttpResponse.text(backgroundDocument(extension), "text/html; charset=utf-8")
          else -> {
            val file = extension.file(path)
            if (!file.isFile || !file.canonicalPath.startsWith(extension.dir.canonicalPath)) {
              HttpResponse(404, body = "Not found: $path".toByteArray())
            } else {
              HttpResponse(200, contentType(file.name), body = file.readBytes())
            }
          }
        }
    return HttpResponse(
        response.status, response.contentType, response.body, response.headers + cors)
  }

  private fun backgroundDocument(extension: Extension): String {
    // The process token busts any cache the WebView kept from an earlier run
    val version = "?v=" + hostToken
    val shim = "<script src=\"/__chromext__.js$version\"></script>"
    val page = extension.backgroundPage
    if (page != null) {
      val html = extension.readText(page) ?: return "<html><body>Missing ${page}</body></html>"
      val headPattern = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
      if (headPattern != null) {
        return html.substring(0, headPattern.range.last + 1) +
            shim +
            html.substring(headPattern.range.last + 1)
      }
      val htmlPattern = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
      if (htmlPattern != null) {
        return html.substring(0, htmlPattern.range.last + 1) +
            shim +
            html.substring(htmlPattern.range.last + 1)
      }
      return shim + html
    }
    val type = if (extension.backgroundIsModule) " type=\"module\"" else ""
    val scripts =
        extension.backgroundScripts.joinToString("\n") {
          "<script$type src=\"/$it$version\"></script>"
        }
    return """
        |<!DOCTYPE html>
        |<html>
        |<head>
        |<meta charset="utf-8">
        |<title>${extension.name}</title>
        |$shim
        |</head>
        |<body>
        |$scripts
        |</body>
        |</html>
        """
        .trimMargin()
  }

  // ---- manager ---------------------------------------------------------------

  private fun serveManager(request: HttpRequest): HttpResponse {
    val path = request.path
    if (path == "/" || path == "/manager.html") {
      return HttpResponse.asset("manager.html", mapOf("__TOKEN__" to token))
    }
    if (path == "/host.html") {
      val list = JSONArray()
      Extensions.enabled()
          .filter { it.hasBackground }
          .forEach { list.put(JSONObject().put("id", it.id).put("port", portOf(it))) }
      return HttpResponse.asset(
          "host.html",
          mapOf(
              "__EXT_LIST__" to list.toString(),
              "__TOKEN__" to token,
              "__HOST_TOKEN__" to hostToken))
    }
    return when {
      path == "/manager.js" -> HttpResponse.asset("manager.js")
      path == "/manager.css" -> HttpResponse.asset("manager.css")
      path == "/host.js" -> HttpResponse.asset("host.js")
      path.startsWith("/_api/") -> {
        val open = path == "/_api/state"
        if (!open && request.query["token"] != token && request.headers["x-token"] != token) {
          HttpResponse(403, body = "Bad token".toByteArray())
        } else {
          runCatching { ManagerApi.handle(path.removePrefix("/_api/"), request) }
              .getOrElse {
                Log.ex(it, "Manager API failure")
                HttpResponse.json(JSONObject().put("error", it.message ?: "error"))
              }
        }
      }
      else -> HttpResponse.notFound()
    }
  }
}

/** Management API used by the local front end. */
object ManagerApi {

  fun handle(action: String, request: HttpRequest): HttpResponse {
    Extensions.ensureLoaded()
    return when (action) {
      "list" -> HttpResponse.json(list())
      "install" -> {
        val name = request.query["name"]
        val extension = Extensions.install(request.body.inputStream(), name)
        ExtServer.portOf(extension)
        HttpResponse.json(JSONObject().put("ok", true).put("id", extension.id))
      }
      "store" -> {
        val id = request.query["id"] ?: ""
        val extension = Extensions.installFromStore(id)
        ExtServer.portOf(extension)
        HttpResponse.json(JSONObject().put("ok", true).put("id", extension.id))
      }
      "toggle" -> {
        val id = request.query["id"] ?: ""
        val enabled = request.query["enabled"] != "0"
        Extensions.setEnabled(id, enabled)
        Extensions.byId(id)?.let { if (enabled) ExtServer.portOf(it) }
        HttpResponse.json(JSONObject().put("ok", true))
      }
      "uninstall" -> {
        val id = request.query["id"] ?: ""
        HttpResponse.json(JSONObject().put("ok", Extensions.uninstall(id)))
      }
      "reload" -> {
        Extensions.reload()
        ExtServer.startAll()
        HttpResponse.json(list())
      }
      "state" ->
          HttpResponse.json(
              JSONObject()
                  .put("background", Background.status())
                  .put("queue", HostQueue.size())
                  .put("hostPath", ExtServer.hostPath())
                  .put("hostUrl", ExtServer.hostUrl())
                  .put("extensions", Extensions.enabled().size))
      "hostmsg" -> {
        if (request.query["p"] != ExtServer.hostToken) {
          // A host page left over from an earlier browser process
          return HttpResponse(403, body = "Stale host page".toByteArray())
        }
        val payload = request.body.toString(Charsets.UTF_8)
        ExtensionRuntime.dispatch(payload, null, null)
        HttpResponse.json(JSONObject().put("ok", true))
      }
      "poll" -> {
        if (request.query["p"] != ExtServer.hostToken) {
          return HttpResponse(403, body = "Stale host page".toByteArray())
        }
        ExtServer.notePoll()
        val commands = HostQueue.take(20000)
        HttpResponse.json(JSONObject().put("commands", JSONArray(commands)))
      }
      "open" -> {
        val id = request.query["id"] ?: ""
        val extension = Extensions.byId(id)
        HttpResponse.json(
            JSONObject()
                .put("ok", extension != null)
                .put("url", extension?.let { ExtServer.origin(it) } ?: ""))
      }
      else -> HttpResponse.json(JSONObject().put("error", "Unknown action $action"))
    }
  }

  private fun list(): JSONObject {
    val extensions = JSONArray()
    Extensions.list.forEach { extension ->
      val icons = JSONObject()
      extension.icons.forEach { (size, path) -> icons.put(size, path) }
      extensions.put(
          JSONObject()
              .put("id", extension.id)
              .put("name", extension.name)
              .put("version", extension.version)
              .put("description", extension.description)
              .put("enabled", Extensions.isEnabled(extension.id))
              .put("manifestVersion", extension.manifestVersion)
              .put("hasBackground", extension.hasBackground)
              .put("contentScripts", extension.contentScripts.size)
              .put("icons", icons)
              .put("port", ExtServer.portOf(extension))
              .put("permissions", JSONArray(extension.permissionWarnings)))
    }
    return JSONObject()
        .put("extensions", extensions)
        .put("directory", Extensions.root.absolutePath)
        .put("origin", "http://127.0.0.1")
  }
}

/** Commands waiting for the extension host page, which polls the loopback server for them. */
object HostQueue {

  private val lock = Object()
  private val pending = java.util.ArrayDeque<String>()

  fun push(code: String) {
    Log.d("HostQueue push: " + code.take(100))
    synchronized(lock) {
      pending.addLast(code)
      if (pending.size > 200) pending.removeFirst()
      lock.notifyAll()
    }
  }

  fun size(): Int = synchronized(lock) { pending.size }

  fun take(timeoutMs: Long): List<String> {
    synchronized(lock) {
      if (pending.isEmpty()) runCatching { lock.wait(timeoutMs) }
      val commands = pending.toList()
      pending.clear()
      if (commands.isNotEmpty()) Log.d("HostQueue take ${commands.size} command(s)")
      return commands
    }
  }
}
