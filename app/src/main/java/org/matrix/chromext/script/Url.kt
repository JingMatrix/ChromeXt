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
    pattern = pattern.replace(".", "\\.")
    pattern = pattern.replace("*", "\\S*")
    pattern = pattern.replace("\\S*\\.", "(\\S*\\.)?")

    val result = Regex(pattern).matches(url)
    Log.d("Matching ${pattern} against ${url}: ${result}")
    return result
  } else {
    return false
  }
}
