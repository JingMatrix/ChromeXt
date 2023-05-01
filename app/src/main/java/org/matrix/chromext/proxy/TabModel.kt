package org.matrix.chromext.proxy

import java.lang.ref.WeakReference
import java.util.Collections
import org.matrix.chromext.utils.invokeMethod

object TabModel {
  private var tabModels = mutableListOf<WeakReference<Any>>()

  fun update(model: Any) {
    tabModels += WeakReference(model)
  }

  fun dropModel(model: Any) {
    tabModels.removeAll { it.get()!! == model }
  }

  private fun index(): Int {
    return tabModels.last().get()!!.invokeMethod() { name == "index" } as Int
  }

  fun getTab(): Any? {
    return tabModels.last().get()!!.invokeMethod(index()) { name == "getTabAt" }
  }

  fun getUrl(): String {
    return UserScriptProxy.parseUrl(getTab()?.invokeMethod { name == "getUrl" }) ?: ""
  }

  fun refresh(tab: Any?) {
    val n = tabModels.size
    if (n > 1 && tab != null && getTab() != tab) {
      // Only fix for incognito mode, should be sufficient for normal usage
      Collections.swap(tabModels, n - 1, n - 2)
    }
  }
}
