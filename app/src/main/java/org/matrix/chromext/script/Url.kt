package org.matrix.chromext.script

import com.github.kyuubiran.ezxhelper.utils.Log
import kotlin.text.Regex

fun urlMatch(match: String, url: String): Boolean {
  var pattern = match
  if (pattern == "https://*" || pattern == "http://*" || pattern == "file://*") {
    return true
  }

  if ("*" !in pattern) {
    return pattern in url
  }

  if ("://" in pattern) {
    pattern = pattern.replace(".", "\\.")
    pattern = pattern.replace("*", "\\S*")
    pattern = pattern.replace("\\S*\\.", "\\S*")

    // pattern = pattern.replace(".", "\\.")
    // pattern = pattern.replace("*", "[\\w\\-]*?")
    // Recover those *. killed by replacements
    // pattern = pattern.replace("[\\w\\-]*?\\.", "[\\w\\-\\.]*?")

    Log.d("Java regex matching ${pattern} against ${url}: ${Regex(pattern).matches(url)}")
    return Regex(pattern).matches(url)
  } else {
    return false
  }
}
