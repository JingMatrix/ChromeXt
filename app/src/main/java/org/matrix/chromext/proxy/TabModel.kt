package org.matrix.chromext.proxy

import android.content.Context
import android.content.SharedPreferences
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.erudaToggle
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

object TabModel {
  private var tabModels = mutableListOf<WeakReference<Any>>()
  private var eruda_loaded = mutableMapOf<Int, Boolean>()
  private var url = mutableMapOf<Int, String>()

  fun update(model: Any, className: String) {
    if (model::class.qualifiedName == className) {
      tabModels += WeakReference(model)
    } else {
      Log.e("updateTabModel: ${model::class.qualifiedName} is not ${className}")
    }
  }

  fun dropModel(model: Any) {
    tabModels.removeAll { it.get()!! == model }
  }

  fun index(): Int {
    return tabModels.last().get()!!.invokeMethod() { name == "index" } as Int
  }

  fun getTab(): Any {
    return tabModels.last().get()!!.invokeMethod(index()) { name == "getTabAt" }!!
  }

  fun getUrl(): String {
    return url.get(index()) ?: ""
  }

  fun refresh(newUrl: String) {
    url.put(index(), newUrl)
    eruda_loaded.put(index(), false)
  }

  fun erudaLoaded(): Boolean {
    return eruda_loaded.get(index()) ?: false
  }

  fun openEruda(): String {
    val ctx = Chrome.getContext()
    var script = ""
    if (!erudaLoaded()) {
      val sharedPref: SharedPreferences = ctx.getSharedPreferences("Eruda", Context.MODE_PRIVATE)
      if (sharedPref.contains("eruda")) {
        script += sharedPref.getString("eruda", "") + "\n"
        script += ctx.assets.open("local_eruda.js").bufferedReader().use { it.readText() }
        script += erudaToggle
        eruda_loaded.put(index(), true)
      } else {
        Log.toast(ctx, "Please update Eruda in the Developer options menu")
      }
    } else {
      script = erudaToggle
    }

    return script
  }
}
