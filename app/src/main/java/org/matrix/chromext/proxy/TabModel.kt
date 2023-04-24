package org.matrix.chromext.proxy

import java.io.File
import java.io.FileReader
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.script.erudaToggle
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

object TabModel {
  private var tabModels = mutableListOf<WeakReference<Any>>()
  private var eruda_loaded = mutableMapOf<Int, Boolean>()

  fun update(model: Any) {
    tabModels += WeakReference(model)
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
    return UserScriptHook.proxy!!.parseUrl(getTab().invokeMethod { name == "getUrl" }!!) ?: ""
  }

  fun refresh() {
    eruda_loaded.put(index(), false)
  }

  fun erudaLoaded(): Boolean {
    return eruda_loaded.get(index()) ?: false
  }

  fun openEruda(): String {
    val ctx = Chrome.getContext()
    var script = ""
    if (!erudaLoaded()) {
      val eruda = File(ctx.getExternalFilesDir(null), "Download/Eruda.js")
      if (eruda.exists()) {
        script += FileReader(eruda).use { it.readText() } + "\n"
        script += ctx.assets.open("local_eruda.js").bufferedReader().use { it.readText() }
        script += erudaToggle
        eruda_loaded.put(index(), true)
      } else {
        Log.toast(ctx, "Updating Eruda...")
        Download.start(ERUD_URL, "Download/Eruda.js", true) {
          Log.toast(ctx, "Eruda is prepared now")
        }
      }
    } else {
      script = erudaToggle
    }

    return script
  }
}
