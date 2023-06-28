package org.matrix.chromext.script

import kotlin.text.Regex
import org.matrix.chromext.utils.Log

fun urlMatch(match: String, url: String, strict: Boolean): Boolean {
  var pattern = match

  if ("*" !in pattern) {
    if (strict) {
      return pattern == url
    } else {
      return pattern in url
    }
  }

  if ("://" in pattern) {
    pattern = pattern.replace("?", "\\?")
    pattern = pattern.replace(".", "\\.")
    pattern = pattern.replace("*", "\\S*")
    pattern = pattern.replace("\\S*\\.", "(\\S*\\.)?")

    runCatching {
          val result = Regex(pattern).matches(url)
          Log.d("Matching ${pattern} against ${url}: ${result}")
          return result
        }
        .onFailure { Log.i("Invaid matching rule: ${match}, error: " + it.message) }
  }
  return false
}
