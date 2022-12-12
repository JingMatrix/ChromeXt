package org.matrix.chromext.hook

abstract class BaseHook {
  var isInit: Boolean = false
  abstract fun init()
  companion object {
    // Use frida-trace to find method for casting GURL to String in org/chromium/url/GURL.smali
    const val SHOW_URL = "j"
    // grep smali code with Tab.loadUrl to get the loadUrl function in
    // org/chromium/chrome/browser/tab/TabImpl.smali
    const val LOAD_URL = "h"
    // get TabImpl field in
    // org/chromium/chrome/browser/tab/TabWebContentsDelegateAndroidImpl.smali
    const val TAB_FIELD = "a"
  }
}
