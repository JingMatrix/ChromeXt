package org.matrix.chromext.proxy

import java.lang.ref.WeakReference
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

object TabModel {
  private var currentTab: WeakReference<Any>? = null

  fun getTab(): Any? {
    return currentTab?.get()
  }

  fun getUrl(): String {
    return UserScriptProxy.parseUrl(getTab()?.invokeMethod { name == "getUrl" }) ?: ""
  }

  fun refresh(tab: Any?) {
    if (tab != null) currentTab = WeakReference(tab)
  }
}
