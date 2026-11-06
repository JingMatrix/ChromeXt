package org.matrix.chromext.extension

import java.io.File
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.utils.Log

/** WebExtension match patterns, following the algorithm Chrome documents. */
object MatchPattern {

  private val cache = HashMap<String, Pattern?>()

  fun compile(pattern: String): Pattern? =
      cache.getOrPut(pattern) {
        runCatching { build(pattern.trim()) }
            .onFailure { Log.d("Invalid match pattern ${pattern}: ${it.message}") }
            .getOrNull()
      }

  private fun build(pattern: String): Pattern? {
    var source = pattern
    if (source == "<all_urls>") source = "*://*/*"
    val separator = source.indexOf("://")
    if (separator < 0) return null
    val scheme = source.substring(0, separator)
    val rest = source.substring(separator + 3)
    val slash = rest.indexOf('/')
    val host = if (slash < 0) rest else rest.substring(0, slash)
    val path = if (slash < 0) "/*" else rest.substring(slash)
    val regex = StringBuilder()
    when (scheme) {
      "*" -> regex.append("(?:http|https)")
      "http",
      "https",
      "file",
      "ftp",
      "ws",
      "wss",
      "urn" -> regex.append(Pattern.quote(scheme))
      else -> return null
    }
    regex.append("://")
    // file:///* has no host component
    if (scheme != "file") {
      when {
        host == "*" -> regex.append("[^/]+")
        host.startsWith("*.") ->
            regex.append("(?:[^/]+\\.)?").append(Pattern.quote(host.substring(2)))
        else -> regex.append(Pattern.quote(host))
      }
    }
    for (c in path) {
      when (c) {
        '*' -> regex.append(".*")
        '?' -> regex.append('.')
        else -> regex.append(Pattern.quote(c.toString()))
      }
    }
    return Pattern.compile("^${regex}$")
  }

  fun matches(pattern: String, url: String): Boolean =
      compile(pattern)?.matcher(url)?.matches() ?: false

  fun matchesAny(patterns: List<String>, url: String): Boolean = patterns.any { matches(it, url) }
}

fun JSONArray.toStringList(): List<String> =
    List(length()) { optString(it) }.filter { it.isNotEmpty() }

private fun JSONObject.optStringList(name: String): List<String> =
    optJSONArray(name)?.toStringList() ?: emptyList()

data class ContentScript(
    val matches: List<String>,
    val excludeMatches: List<String>,
    val includeGlobs: List<String>,
    val excludeGlobs: List<String>,
    val js: List<String>,
    val css: List<String>,
    val runAt: String,
    val allFrames: Boolean,
    val matchAboutBlank: Boolean,
) {
  fun matches(url: String): Boolean {
    if (matches.isEmpty()) return false
    if (MatchPattern.matchesAny(excludeMatches, url)) return false
    if (includeGlobs.isNotEmpty() && includeGlobs.none { globMatch(it, url) }) return false
    if (excludeGlobs.any { globMatch(it, url) }) return false
    return MatchPattern.matchesAny(matches, url)
  }

  companion object {
    // Chrome wraps include_globs with "*" and treats them as globs on the whole URL
    private fun globMatch(glob: String, url: String): Boolean {
      val regex =
          glob
              .map { if (it == '*') ".*" else if (it == '?') "." else Regex.escape(it.toString()) }
              .joinToString("")
      return runCatching { Regex(regex).matches(url) }.getOrDefault(false)
    }

    fun parse(json: JSONObject): ContentScript? {
      val matches = json.optStringList("matches")
      if (matches.isEmpty()) return null
      return ContentScript(
          matches = matches,
          excludeMatches = json.optStringList("exclude_matches"),
          includeGlobs = json.optStringList("include_globs"),
          excludeGlobs = json.optStringList("exclude_globs"),
          js = json.optStringList("js"),
          css = json.optStringList("css"),
          runAt = json.optString("run_at", "document_idle"),
          allFrames = json.optBoolean("all_frames", false),
          matchAboutBlank = json.optBoolean("match_about_blank", false),
      )
    }
  }
}

/** An unpacked extension living in its own directory. */
class Extension(val dir: File) {

  val id: String = dir.name
  val manifest: JSONObject
  val manifestVersion: Int
  val defaultLocale: String?
  val messages: JSONObject?
  private val rawName: String
  private val rawDescription: String

  val backgroundPage: String?
  val backgroundScripts: List<String>
  val backgroundIsModule: Boolean
  val isServiceWorker: Boolean
  val contentScripts: List<ContentScript>
  val dnrRuleResources: List<String>
  val webAccessibleResources: List<String>
  val permissionWarnings: List<String>
  val icons: Map<String, String>

  init {
    val file = File(dir, "manifest.json")
    manifest = JSONObject(file.readText().removePrefix("\uFEFF"))
    manifestVersion = manifest.optInt("manifest_version", 2)
    defaultLocale = manifest.optString("default_locale").ifEmpty { null }
    messages = defaultLocale?.let { loadMessages(it) }
    rawName = manifest.optString("name", id)
    rawDescription = manifest.optString("description")

    val background = manifest.optJSONObject("background")
    if (background != null) {
      val page = background.optString("page")
      val worker = background.optString("service_worker")
      when {
        page.isNotEmpty() -> {
          backgroundPage = normalize(page)
          backgroundScripts = emptyList()
        }
        worker.isNotEmpty() -> {
          // A MV3 service worker runs as a page: no worker lifecycle, but the code path is the same
          backgroundPage = null
          backgroundScripts = listOf(normalize(worker))
        }
        else -> {
          backgroundPage = null
          backgroundScripts = background.optStringList("scripts").map { normalize(it) }
        }
      }
      backgroundIsModule = background.optString("type") == "module"
      isServiceWorker = background.optString("service_worker").isNotEmpty()
    } else {
      backgroundPage = null
      backgroundScripts = emptyList()
      backgroundIsModule = false
      isServiceWorker = false
    }

    val scripts = manifest.optJSONArray("content_scripts")
    contentScripts =
        if (scripts == null) emptyList()
        else
            (0 until scripts.length())
                .mapNotNull { scripts.optJSONObject(it) }
                .mapNotNull { ContentScript.parse(it) }

    dnrRuleResources =
        manifest.optJSONObject("declarative_net_request")?.optJSONArray("rule_resources")?.let {
            resources ->
          (0 until resources.length()).mapNotNull { index ->
            val entry = resources.optJSONObject(index) ?: return@mapNotNull null
            if (entry.optBoolean("enabled", true)) entry.optString("path").ifEmpty { null }
            else null
          }
        } ?: emptyList()

    webAccessibleResources =
        when (val resources = manifest.opt("web_accessible_resources")) {
          is JSONArray -> resources.toStringList()
          is JSONObject -> resources.optStringList("resources")
          else -> emptyList()
        }

    val permissions =
        manifest.optStringList("permissions") + manifest.optStringList("host_permissions")
    permissionWarnings = permissions

    icons = mutableMapOf()
    manifest.optJSONObject("icons")?.let { json ->
      json.keys().forEach { key -> icons[key] = json.optString(key) }
    }
  }

  private fun loadMessages(locale: String): JSONObject? = runCatching {
    val file = File(File(File(dir, "_locales"), locale), "messages.json")
    JSONObject(file.readText().removePrefix("\uFEFF"))
  }
      .getOrNull()

  private fun normalize(path: String): String = path.removePrefix("/")

  /** Replace __MSG_key__ placeholders the way Chrome does. */
  fun resolve(text: String): String {
    if (!text.contains("__MSG_")) return text
    val sb = StringBuilder()
    var index = 0
    while (true) {
      val start = text.indexOf("__MSG_", index)
      if (start < 0) break
      val end = text.indexOf("__", start + 6)
      if (end < 0) break
      val key = text.substring(start + 6, end)
      sb.append(text, index, start).append(message(key))
      index = end + 2
    }
    sb.append(text, index, text.length)
    return sb.toString()
  }

  fun message(name: String, substitutions: List<String> = emptyList()): String {
    val entry = messages?.optJSONObject(name) ?: return ""
    var text = entry.optString("message")
    substitutions.forEachIndexed { i, value ->
      text = text.replace("\$${i + 1}", value).replace("$${i + 1}", value)
    }
    return text.replace("$$", "$")
  }

  val name: String by lazy { resolve(rawName) }
  val description: String by lazy { resolve(rawDescription) }
  val version: String by lazy { manifest.optString("version", "0.0.0") }
  val hasBackground: Boolean by lazy { backgroundPage != null || backgroundScripts.isNotEmpty() }

  fun file(path: String): File = File(dir, path.removePrefix("/"))

  fun readText(path: String): String? = runCatching {
    val file = file(path)
    if (!file.isFile) {
      Log.d("Missing extension resource ${id}/${path}")
      return null
    }
    file.readText().removePrefix("\uFEFF")
  }
      .onFailure { Log.e("Fail to read ${id}/${path}: ${it.message}") }
      .getOrNull()
}
