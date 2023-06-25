package org.matrix.chromext

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

object Chrome {
  private var mContext: WeakReference<Activity>? = null
  private var currentTab: WeakReference<Any>? = null
  private var tabModels = mutableListOf<WeakReference<Any>>()

  var isDev = false
  var isEdge = false
  var isVivaldi = false
  var isBrave = false

  fun init(ctx: Context) {
    val initialized = mContext != null
    if (ctx is Activity) {
      mContext = WeakReference(ctx)
    } else {
      Log.e("Chrome.init called with a non-activity context")
    }

    if (initialized) return
    val packageName = ctx.getPackageName()
    @Suppress("DEPRECATION")
    val packageInfo = ctx.getPackageManager().getPackageInfo(packageName, 0)
    isEdge = packageName.startsWith("com.microsoft.emmx")
    isVivaldi = packageName == "com.vivaldi.browser"
    isBrave = packageName.startsWith("com.brave.browser")
    isDev = packageName.endsWith("canary") || packageName.endsWith("dev")
    Log.i("Package: ${packageName}, v${packageInfo!!.versionName}")
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
    if (UserScriptHook.isInit) {
      // Log.d("Broadcasting ${event} ${data})")
      tabModels.forEach {
        val count = it.get()!!.invokeMethod() { name == "getCount" } as Int
        for (i in 0.rangeTo(count)) {
          val tab = it.get()?.invokeMethod(i) { name == "getTabAt" }
          UserScriptProxy.loadUrl(
              "javascript: window.dispatchEvent(new CustomEvent('${event}', ${data}));", tab)
        }
      }
    }
  }
}
