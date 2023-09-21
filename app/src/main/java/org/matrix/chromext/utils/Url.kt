package org.matrix.chromext.utils

import android.net.Uri
import android.provider.OpenableColumns
import java.net.URL
import kotlin.text.Regex
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.Script

const val ERUD_URL = "https://cdn.jsdelivr.net/npm/eruda"
private const val DEV_FRONT_END = "https://chrome-devtools-frontend.appspot.com"

fun randomString(length: Int): String {
  val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
  return List(length) { alphabet.random() }.joinToString("")
}

private fun urlMatch(match: String, url: String, strict: Boolean): Boolean {
  var pattern = match
  val regexPattern = pattern.startsWith("/") && pattern.endsWith("/")

  if (regexPattern) {
    pattern = pattern.removeSurrounding("/", "/")
    pattern = pattern.replace("\\/", "/")
    pattern = pattern.replace("\\://", "://")
  } else if ("*" !in pattern) {
    if (strict) {
      return pattern == url
    } else {
      return pattern in url
    }
  } else if ("://" in pattern || strict) {
    pattern = pattern.replace("?", "\\?")
    pattern = pattern.replace(".", "\\.")
    pattern = pattern.replace("*", "[^:]*")
    pattern = pattern.replace("[^:]*\\.", "([^:]*\\.)?")
  } else {
    return false
  }

  runCatching {
        val result =
            if (regexPattern) {
              Regex(pattern).containsMatchIn(url)
            } else {
              Regex(pattern).matches(url)
            }
        return result
      }
      .onFailure { Log.i("Invaid matching rule: ${match}, error: " + it.message) }
  return false
}

fun matching(script: Script, url: String): Boolean {
  script.exclude.forEach {
    if (urlMatch(it, url, true)) {
      return false
    }
  }
  script.match.forEach {
    if (urlMatch(it, url, false)) {
      // Log.d("${script.id} injected")
      return true
    }
  }
  return false
}

fun isDevToolsFrontEnd(url: String?): Boolean {
  if (url == null) return false
  return url.startsWith(DEV_FRONT_END)
}

private val invalidUserScriptDomains = listOf("github.com")
val invalidUserScriptUrls = mutableListOf<String>()

fun isUserScript(url: String?): Boolean {
  if (url == null) return false
  if (url.endsWith(".user.js")) {
    if (invalidUserScriptUrls.contains(url)) return false
    if (invalidUserScriptDomains.contains(URL(url).getAuthority())) return false
    return true
  } else {
    return resolveContentUrl(url)?.endsWith(".js") == true
  }
}

fun resolveContentUrl(url: String): String? {
  if (!url.startsWith("content://")) return null
  Chrome.getContext().contentResolver.query(Uri.parse(url), null, null, null, null)?.use { cursor ->
    cursor.moveToFirst()
    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    val dataIndex = cursor.getColumnIndex("_data")
    if (dataIndex != -1) {
      return cursor.getString(dataIndex)
    } else {
      return cursor.getString(nameIndex)
    }
  }
  return null
}

private val trustedHosts =
    listOf("jingmatrix.github.io", "jianyu-ma.onrender.com", "jianyu-ma.netlify.app")

fun isChromeXtFrontEnd(url: String?): Boolean {
  if (url == null || !url.endsWith("/ChromeXt/")) return false
  trustedHosts.forEach { if (url == "https://" + it + "/ChromeXt/") return true }
  return false
}

private val sandboxHosts = listOf("raw.githubusercontent.com")

fun shouldBypassSandbox(url: String?): Boolean {
  sandboxHosts.forEach { if (url?.startsWith("https://" + it) == true) return true }
  return false
}

fun parseOrigin(url: String): String? {
  val protocol = url.split("://")
  if (protocol.size > 1 && arrayOf("https", "http", "file").contains(protocol.first())) {
    return protocol.first() + "://" + protocol[1].split("/").first()
  } else {
    return null
  }
}
