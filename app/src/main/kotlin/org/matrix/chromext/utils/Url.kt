package org.matrix.chromext.utils

import android.net.Uri
import android.provider.OpenableColumns
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

fun isUserScript(url: String?, path: String? = null): Boolean {
  if (url == null) return false
  if (url.endsWith(".user.js")) {
    if (invalidUserScriptUrls.contains(url)) return false
    invalidUserScriptDomains.forEach { if (url.startsWith("https://" + it) == true) return false }
    return true
  } else {
    return (path ?: resolveContentUrl(url)).endsWith(".js")
  }
}

fun resolveContentUrl(url: String): String {
  if (!url.startsWith("content://")) return ""
  Chrome.getContext().contentResolver.query(Uri.parse(url), null, null, null, null)?.use { cursor ->
    cursor.moveToFirst()
    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    val dataIndex = cursor.getColumnIndex("_data")
    if (dataIndex != -1) {
      return cursor.getString(dataIndex) ?: cursor.getString(nameIndex)
    } else {
      return cursor.getString(nameIndex)
    }
  }
  return ""
}

private val trustedHosts =
    listOf(
        // jingmatrix.github.io now answers with a 301 to this domain, so the browser never shows
        // the github.io URL and the front end has to be recognised under its new name as well.
        "jing.matrix-transformation.dev",
        "jingmatrix.github.io",
        "jianyu-ma.onrender.com",
        "jianyu-ma.netlify.app",
    )

fun isChromeXtFrontEnd(url: String?): Boolean {
  if (url == null) return false
  // The host is still matched exactly, which is what grants the page access to GM.ChromeXt; only
  // the one fixed path is compared without regard to case, because the netlify mirror lower cases
  // it to /chromext/ before serving the very same page.
  trustedHosts.forEach {
    if (url.equals("https://" + it + "/ChromeXt/", ignoreCase = true)) return true
  }
  return false
}

private val sandboxHosts = listOf("raw.githubusercontent.com", "gist.githubusercontent.com")

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

fun isChromeScheme(url: String): Boolean {
  val protocol = url.split("://")
  return (protocol.size > 1 && arrayOf("chrome", "chrome-native").contains(protocol.first()))
}
