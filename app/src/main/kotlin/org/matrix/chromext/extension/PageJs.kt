package org.matrix.chromext.extension

import android.os.Handler
import android.os.Looper
import java.lang.reflect.Proxy
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

/**
 * Evaluates JavaScript in a page through `WebContents#evaluateJavaScript`.
 *
 * Going through `javascript:` URLs reloads the navigation machinery and is dropped in some states,
 * which is fatal for a request/response protocol, so prefer the direct WebContents entry point and
 * fall back to the URL based one only when it is missing.
 */
object PageJs {

  private val callback: Any? by lazy {
    runCatching {
      val clazz =
          Chrome.getContext()
              .classLoader
              .loadClass("org.chromium.content_public.browser.JavaScriptCallback")
      Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, _, _ -> null }
    }
        .getOrNull()
  }

  fun evaluate(tab: Any?, script: String): Boolean {
    if (tab == null || script.isEmpty()) return false
    val webContents =
        runCatching { tab.invokeMethod { name == "getWebContents" } }.getOrNull() ?: return false
    val method =
        webContents.javaClass.methods.firstOrNull {
          (it.name == "evaluateJavaScript" || it.name == "evaluateJavascript") &&
              it.parameterTypes.size == 2 &&
              it.parameterTypes[0] == String::class.java
        } ?: return false
    method.isAccessible = true
    val argument =
        callback
            ?: runCatching {
              val clazz = method.parameterTypes[1]
              Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, _, _ -> null }
            }
                .getOrNull()
    if (argument == null) return false
    Handler(Looper.getMainLooper()).post {
      runCatching { method.invoke(webContents, script, argument) }
          .onFailure { Log.ex(it, "Fail to evaluate JavaScript in page") }
    }
    return true
  }
}
