package org.matrix.chromext.script

import kotlin.text.Regex
import org.matrix.chromext.devtools.DEV_FRONT_END
import org.matrix.chromext.utils.Log

private fun urlMatch(match: String, url: String, strict: Boolean): Boolean {
  var pattern = match
  val regexPattern = pattern.startsWith("/") && pattern.endsWith("/")

  if (regexPattern) {
    pattern = pattern.removeSurrounding("/", "/")
  } else if ("*" !in pattern) {
    if (strict) {
      return pattern == url
    } else {
      return pattern in url
    }
  } else if ("://" in pattern) {
    pattern = pattern.replace("?", "\\?")
    pattern = pattern.replace(".", "\\.")
    pattern = pattern.replace("*", "\\S[^:]*")
    pattern = pattern.replace("\\S[^:]*\\.", "(\\S[^:]*\\.)?")
  } else {
    return false
  }

  runCatching {
        val result = Regex(pattern).matches(url)
        // Log.d("Matching ${pattern} against ${url}: ${result}")
        return result
      }
      .onFailure { Log.i("Invaid matching rule: ${match}, error: " + it.message) }
  return false
}

fun matching(script: Script, url: String): Boolean {
  if (url.endsWith("/ChromeXt/") || url.endsWith(".user.js") || url.startsWith(DEV_FRONT_END)) {
    return false
  }

  if (!url.startsWith("http")) {
    return false
  }

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
