package org.matrix.chromext.extension

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

/** Registry of unpacked extensions, their enabled state and their persistent storage. */
object Extensions {

  private const val PREFS = "ExtensionState"
  private const val DIR_NAME = "Extension"

  private var loaded = false
  private val enabledState = mutableMapOf<String, Boolean>()

  val list = mutableListOf<Extension>()

  private val prefs: android.content.SharedPreferences
    get() = Chrome.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  val root: File
    get() {
      val base =
          Chrome.getContext().getExternalFilesDir(null)
              ?: File(Chrome.getContext().filesDir, "external")
      return File(base, DIR_NAME)
    }

  private val storageDir: File
    get() = File(Chrome.getContext().filesDir, "ExtensionStorage")

  fun ensureLoaded() {
    if (loaded) return
    loaded = true
    reload()
  }

  fun reload() {
    list.clear()
    enabledState.clear()
    val dir = root
    if (!dir.isDirectory) {
      Log.d("No extension directory at ${dir}")
      return
    }
    dir.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name }
        ?.forEach { sub ->
          val manifest = File(sub, "manifest.json")
          if (!manifest.isFile) return@forEach
          runCatching { Extension(sub) }
              .onSuccess {
                list.add(it)
                enabledState[it.id] = prefs.getBoolean("enabled.${it.id}", true)
              }
              .onFailure { Log.e("Fail to load extension ${sub.name}: ${it.message}") }
        }
    Log.i("Loaded ${list.size} extension(s): ${list.joinToString { it.id }}")
    // Start the host right away: extensions register their webRequest listeners there, and the
    // first navigation of a session would otherwise happen before they exist
    if (enabled().any { it.hasBackground }) runCatching { Background.ensure() }
  }

  fun byId(id: String): Extension? {
    ensureLoaded()
    return list.find { it.id == id }
  }

  fun enabled(): List<Extension> {
    ensureLoaded()
    return list.filter { isEnabled(it.id) }
  }

  fun isEnabled(id: String): Boolean {
    ensureLoaded()
    return enabledState[id] ?: prefs.getBoolean("enabled.$id", true)
  }

  fun setEnabled(id: String, enabled: Boolean) {
    enabledState[id] = enabled
    if (!enabled) Blocking.forget(id)
    prefs.edit().putBoolean("enabled.$id", enabled).apply()
    if (!enabled) ExtServer.stop(byId(id))
  }

  /** Pairs of extension/content script matching the given url. */
  fun matching(url: String): List<Pair<Extension, ContentScript>> {
    if (!url.startsWith("http")) return emptyList()
    // Never inject into ChromeXt's own pages
    if (ExtServer.isOwnPage(url)) return emptyList()
    val result = mutableListOf<Pair<Extension, ContentScript>>()
    enabled().forEach { extension ->
      extension.contentScripts.forEach { script ->
        if (script.matches(url)) result.add(extension to script)
      }
    }
    return result
  }

  // ---- storage ----------------------------------------------------------------

  private val storageCache = mutableMapOf<String, JSONObject>()

  fun storage(id: String): JSONObject =
      storageCache.getOrPut(id) {
        val file = File(storageDir, "$id.json")
        val json = runCatching {
          JSONObject(file.readText())
        }
            .getOrElse { JSONObject().put("local", JSONObject()).put("sync", JSONObject()) }
        if (!json.has("local")) json.put("local", JSONObject())
        if (!json.has("sync")) json.put("sync", JSONObject())
        json
      }

  fun saveStorage(id: String) {
    val json = storageCache[id] ?: return
    runCatching {
      storageDir.mkdirs()
      val file = File(storageDir, "$id.json")
      val tmp = File(storageDir, "$id.json.tmp")
      tmp.writeText(json.toString())
      tmp.renameTo(file)
    }
        .onFailure { Log.e("Fail to save storage of $id: ${it.message}") }
  }

  // ---- install / uninstall ----------------------------------------------------

  fun uninstall(id: String): Boolean {
    val extension = byId(id) ?: return false
    Blocking.forget(id)
    ExtensionRuntime.forget(id)
    ExtServer.stop(extension)
    storageCache.remove(id)
    runCatching { File(storageDir, "$id.json").delete() }
    prefs.edit().remove("enabled.$id").apply()
    enabledState.remove(id)
    list.removeAll(list.filter { it.id == id })
    return extension.dir.deleteRecursively()
  }

  /** Install an unpacked (zipped) extension from any stream. */
  fun install(input: InputStream, fileName: String? = null, expectedId: String? = null): Extension {
    val tmp = File(root, ".install-${System.currentTimeMillis()}")
    try {
      tmp.deleteRecursively()
      tmp.mkdirs()
      unzip(input, tmp)
      // Some archives wrap everything in a single directory
      val source =
          if (File(tmp, "manifest.json").isFile) tmp
          else
              tmp.listFiles()
                  ?.filter { it.isDirectory }
                  ?.filter { File(it, "manifest.json").isFile }
                  ?.singleOrNull()
                  ?: throw IllegalStateException(
                      "manifest.json not found in ${fileName ?: "archive"}")
      val manifestFile = File(source, "manifest.json")
      val manifest = JSONObject(manifestFile.readText().removePrefix("\uFEFF"))
      val id =
          expectedId
              ?: extensionId(manifest)
              ?: sanitizeId(
                  runCatching { Extension(source).name }
                      .getOrElse { manifest.optString("name", "extension") })
      val target = File(root, id)
      if (tmp != source) {
        // Move the nested directory out of the temporary one
        val moved = File(root, ".install-${System.currentTimeMillis()}")
        if (!source.renameTo(moved)) throw IllegalStateException("Fail to move unpacked files")
        tmp.deleteRecursively()
        installInto(moved, target)
      } else {
        installInto(source, target)
      }
      prefs.edit().putBoolean("enabled.$id", prefs.getBoolean("enabled.$id", true)).apply()
      loaded = false
      ensureLoaded()
      Log.i("Installed extension $id")
      return byId(id) ?: throw IllegalStateException("Installed extension $id cannot be loaded")
    } finally {
      tmp.deleteRecursively()
    }
  }

  private fun installInto(source: File, target: File) {
    if (target.exists()) {
      // Keep the old copy around until the new one is in place
      val backup = File(target.parentFile, ".old-${target.name}-${System.currentTimeMillis()}")
      if (!target.renameTo(backup)) target.deleteRecursively()
      if (!source.renameTo(target)) {
        backup.renameTo(target)
        throw IllegalStateException("Fail to replace ${target.name}")
      }
      backup.deleteRecursively()
    } else if (!source.renameTo(target)) {
      throw IllegalStateException("Fail to install into ${target.name}")
    }
  }

  private fun unzip(input: InputStream, target: File) {
    ZipInputStream(input).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        val name = entry.name
        val file = File(target, name)
        // Zip slip guard
        if (!file.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
          Log.e("Skip unsafe zip entry $name")
          continue
        }
        if (entry.isDirectory) {
          file.mkdirs()
        } else {
          file.parentFile?.mkdirs()
          file.outputStream().use { zip.copyTo(it) }
        }
        zip.closeEntry()
      }
    }
  }

  /** Chrome derives the id from the public key shipped in the manifest. */
  private fun extensionId(manifest: JSONObject): String? {
    val key = manifest.optString("key")
    if (key.isEmpty()) return null
    val bytes =
        runCatching { android.util.Base64.decode(key, android.util.Base64.DEFAULT) }.getOrNull()
            ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).take(16)
    val sb = StringBuilder()
    digest.forEach { byte ->
      val v = byte.toInt() and 0xFF
      sb.append('a' + (v shr 4)).append('a' + (v and 0x0F))
    }
    return sb.toString()
  }

  private fun sanitizeId(name: String): String {
    val base = name.lowercase().replace(Regex("[^a-z0-9]"), "-").trim('-').ifEmpty { "extension" }
    var id = base
    var index = 1
    while (File(root, id).exists()) id = "$base-${index++}"
    return id
  }

  /** Install a .crx downloaded from the Web Store. */
  fun installCrx(bytes: ByteArray): Extension {
    val id = crxId(bytes)
    val payload = stripCrxHeader(bytes)
    return install(payload.inputStream(), expectedId = id)
  }

  private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
    var value = 0L
    var shift = 0
    var index = start
    while (index < data.size && shift < 64) {
      val byte = data[index].toInt() and 0xFF
      value = value or ((byte and 0x7F).toLong() shl shift)
      index += 1
      if (byte and 0x80 == 0) return value to index
      shift += 7
    }
    return null
  }

  private fun idFromBytes(bytes: ByteArray): String {
    val sb = StringBuilder()
    bytes.forEach { byte ->
      val value = byte.toInt() and 0xFF
      sb.append('a' + (value shr 4)).append('a' + (value and 0x0F))
    }
    return sb.toString()
  }

  /** The extension id lives in the CRX3 signed header, in a nested protobuf message. */
  private fun crxId(crx: ByteArray): String? {
    if (crx.size < 16 || String(crx, 0, 4, Charsets.US_ASCII) != "Cr24") return null
    if ((crx[4].toInt() and 0xFF) != 3) return null
    val headerLength =
        (crx[8].toInt() and 0xFF) or
            ((crx[9].toInt() and 0xFF) shl 8) or
            ((crx[10].toInt() and 0xFF) shl 16) or
            ((crx[11].toInt() and 0xFF) shl 24)
    val end = minOf(crx.size, 12 + headerLength)
    var signedHeader: ByteArray? = null
    var index = 12
    while (index < end) {
      val (tag, afterTag) = readVarint(crx, index) ?: return null
      index = afterTag
      val field = (tag shr 3).toInt()
      when ((tag and 7).toInt()) {
        0 -> index = readVarint(crx, index)?.second ?: return null
        2 -> {
          val (length, afterLength) = readVarint(crx, index) ?: return null
          index = afterLength
          if (field == 10000) {
            signedHeader = crx.copyOfRange(index, minOf(end, index + length.toInt()))
          }
          index += length.toInt()
        }
        1 -> index += 8
        5 -> index += 4
        else -> return null
      }
    }
    val data = signedHeader ?: return null
    index = 0
    while (index < data.size) {
      val (tag, afterTag) = readVarint(data, index) ?: return null
      index = afterTag
      val field = (tag shr 3).toInt()
      when ((tag and 7).toInt()) {
        0 -> index = readVarint(data, index)?.second ?: return null
        2 -> {
          val (length, afterLength) = readVarint(data, index) ?: return null
          index = afterLength
          if (field == 1 && length == 16L) {
            return idFromBytes(data.copyOfRange(index, index + 16))
          }
          index += length.toInt()
        }
        1 -> index += 8
        5 -> index += 4
        else -> return null
      }
    }
    return null
  }

  private fun stripCrxHeader(bytes: ByteArray): ByteArray {
    fun intAt(offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    val magic = String(bytes, 0, 4, Charsets.US_ASCII)
    if (magic != "Cr24" || bytes.size < 16) return bytes
    val offset =
        when (val version = intAt(4)) {
          3 -> 12 + intAt(8)
          2 -> 16 + intAt(8) + intAt(12)
          else -> throw IllegalStateException("Unsupported CRX version $version")
        }
    if (offset >= bytes.size) throw IllegalStateException("Corrupted CRX header")
    return bytes.copyOfRange(offset, bytes.size)
  }

  /** Download and install an extension from the Chrome Web Store, by its 32 letters id. */
  fun installFromStore(id: String): Extension {
    val version = Chrome.version?.removePrefix("v") ?: "120.0.0.0"
    val url =
        "https://clients2.google.com/service/update2/crx?response=redirect" +
            "&prodversion=$version&acceptformat=crx2,crx3&x=id%3D$id%26uc"
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 20000
    connection.readTimeout = 60000
    connection.requestMethod = "GET"
    try {
      val code = connection.responseCode
      if (code != 200) throw IllegalStateException("Web Store responded $code")
      val bytes = connection.inputStream.use { it.readBytes() }
      Log.i("Downloaded ${bytes.size} bytes for extension $id")
      return installCrx(bytes)
    } finally {
      connection.disconnect()
    }
  }

  /** A 32 character extension id, or a full Web Store url. */
  fun parseStoreId(text: String): String? {
    val trimmed = text.trim()
    Regex("[a-p]{32}").find(trimmed)?.let {
      return it.value
    }
    return null
  }
}
