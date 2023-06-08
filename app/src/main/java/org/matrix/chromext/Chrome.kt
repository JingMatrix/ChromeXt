package org.matrix.chromext

import android.content.Context
import java.lang.ref.WeakReference
import org.matrix.chromext.utils.Log

object Chrome {
  private var mContext: WeakReference<Context>? = null
  private var currentTab: WeakReference<Any>? = null
  var isDev = false
  var isEdge = false
  var isVivaldi = false

  fun init(ctx: Context) {
    val initialized = mContext != null
    mContext = WeakReference(ctx)

    if (initialized) return
    val packageName = ctx.getPackageName()
    @Suppress("DEPRECATION")
    val packageInfo = ctx.getPackageManager().getPackageInfo(packageName, 0)
    isEdge = packageName.startsWith("com.microsoft.emmx")
    isVivaldi = packageName == "com.vivaldi.browser"
    isDev = packageName.endsWith("canary") || packageName.endsWith("dev") || isVivaldi
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
}
