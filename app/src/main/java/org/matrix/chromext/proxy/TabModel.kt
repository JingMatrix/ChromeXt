package org.matrix.chromext

import android.content.Context
import android.content.SharedPreferences
import org.matrix.chromext.script.erudaToggle
import org.matrix.chromext.settings.DownloadEruda
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

object TabModel {
  private var split = false
  private var tabModel: Any? = null
  private var eruda_loaded = mutableMapOf<Int, Boolean>()
  private var eruda_font_fixed = mutableMapOf<Int, Boolean>()
  private var eruda_font_fix: String? = null

  fun update(model: Any, className: String, isSplit: Boolean) {
    split = isSplit
    if (model::class.qualifiedName == className) {
      tabModel = model
    } else {
      Log.e("updateTabModel: ${model::class.qualifiedName} is not ${className}")
    }
  }

  fun index(): Int {
    return tabModel!!.invokeMethod() { name == "index" } as Int
  }

  fun getTab(): Any {
    return tabModel!!.invokeMethod(index()) { name == "getTabAt" }!!
  }

  fun refresh() {
    eruda_loaded.put(index(), false)
    eruda_font_fixed.put(index(), false)
  }

  fun erudaLoaded(): Boolean {
    return eruda_loaded.get(index()) ?: false
  }

  fun erudaFontFixed(): Boolean {
    return eruda_font_fixed.get(index()) ?: false
  }

  fun openEruda(ctx: Context): String {
    var script = ""
    if (!erudaLoaded()) {
      val sharedPref: SharedPreferences = ctx.getSharedPreferences("Eruda", Context.MODE_PRIVATE)
      if (sharedPref.contains("eruda")) {
        script += sharedPref.getString("eruda", "") + "\n"
        script += ctx.assets.open("local_eruda.js").bufferedReader().use { it.readText() }
        script += erudaToggle
        eruda_loaded.put(index(), true)
      } else {
        Log.toast(ctx, "Eruda not found, start downloading it")
        if (!split) {
          // Since one cannot donwload it from the preference menu
          DownloadEruda(ctx).download()
        }
      }
    } else {
      script += erudaToggle
      if (erudaFontFixed()) {
        script += getEurdaFontFix(ctx)
      }
    }

    return script
  }

  fun getEurdaFontFix(ctx: Context): String {
    if (!erudaLoaded()) {
      return ""
    }
    if (eruda_font_fix == null) {
      eruda_font_fix = ctx.assets.open("eruda_font.js").bufferedReader().use { it.readText() }
    }
    eruda_font_fixed.put(index(), true)
    return eruda_font_fix!!
  }
}
