package org.matrix.chromext

import android.app.Application
import android.content.Context
import java.lang.ref.WeakReference
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

object Chrome {
  private var mContext: WeakReference<Context>? = null
  private var currentTab: WeakReference<Any>? = null
  private var tabModels = mutableListOf<WeakReference<Any>>()

  var isDev = false
  var isEdge = false
  var isVivaldi = false
  var isBrave = false

  fun init(ctx: Context, packageName: String? = null) {
    val initialized = mContext != null
    mContext = WeakReference(ctx)

    if (initialized || packageName == null) return

    if (ctx is Application) {
      Log.d("Started a WebView based browser")
    }
    isEdge = packageName.startsWith("com.microsoft.emmx")
    isVivaldi = packageName == "com.vivaldi.browser"
    isBrave = packageName.startsWith("com.brave.browser")
    isDev = packageName.endsWith("canary") || packageName.endsWith("dev")
    @Suppress("DEPRECATION")
    val packageInfo = ctx.getPackageManager()?.getPackageInfo(packageName, 0)
    Log.i("Package: ${packageName}, v${packageInfo?.versionName}")
  }

  fun getContext(): Context {
    return mContext!!.get()!!
  }

  fun load(className: String): Class<*> {
    return getContext().getClassLoader().loadClass(className)
  }

  fun getTab(): Any? {
    return currentTab?.get()
  }

  fun refreshTab(tab: Any?) {
    if (tab != null) currentTab = WeakReference(tab)
  }

  fun addTabModel(model: Any) {
    tabModels += WeakReference(model)
  }

  fun dropTabModel(model: Any) {
    tabModels.removeAll { it.get()!! == model }
  }

  fun broadcast(event: String, data: String) {
    val code = "window.dispatchEvent(new CustomEvent('${event}', ${data}));"
    if (UserScriptHook.isInit) {
      // Log.d("Broadcasting ${event} ${data})")
      tabModels.forEach {
        val count = it.get()!!.invokeMethod() { name == "getCount" } as Int
        for (i in 0.rangeTo(count)) {
          val tab = it.get()?.invokeMethod(i) { name == "getTabAt" }
          UserScriptProxy.loadUrl("javascript: " + code, tab)
        }
      }
    } else if (WebViewHook.isInit) {
      WebViewHook.evaluateJavascript(code)
      Log.w("Broadcasting not implemented yet for event ${event}")
    }
  }
}
