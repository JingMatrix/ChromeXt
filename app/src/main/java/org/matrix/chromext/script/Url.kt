package org.matrix.chromext.script

import kotlin.text.Regex
import org.matrix.chromext.utils.Log

fun urlMatch(match: String, url: String): Boolean {
  var pattern = match

  if ("*" !in pattern) {
    return pattern == url
  }

  if ("://" in pattern) {
    pattern = pattern.replace(".", "\\.")
    pattern = pattern.replace("*", "\\S*")
    pattern = pattern.replace("\\S*\\.", "\\S*")

    // pattern = pattern.replace(".", "\\.")
    // pattern = pattern.replace("*", "[\\w\\-]*?")
    // Recover those *. killed by replacements
    // pattern = pattern.replace("[\\w\\-]*?\\.", "[\\w\\-\\.]*?")

    val result = Regex(pattern).matches(url)
    Log.d("Matching ${pattern} against ${url}: ${result}")
    return result
  } else {
    return false
  }
}
