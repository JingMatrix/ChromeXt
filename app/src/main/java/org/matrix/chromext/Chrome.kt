package org.matrix.chromext

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import java.lang.ref.WeakReference
import org.matrix.chromext.utils.Log

object Chrome {
  private var mContext: WeakReference<Context>? = null
  private var packageInfo: PackageInfo? = null
  private var longVersion: Long = 0
  var split = true
  var isDev = false
  var version = 108
  // Might be extended to support different versions

  fun init(ctx: Context) {
    mContext = WeakReference(ctx)
    val packageName = ctx.getPackageName()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      packageInfo = ctx.getPackageManager().getPackageInfo(packageName, PackageInfoFlags.of(0))
    } else {
      @Suppress("DEPRECATION")
      packageInfo = ctx.getPackageManager().getPackageInfo(packageName, 0)
    }
    isDev = packageName.contains("canary") || packageName.contains("dev")
    var state = "split"
    if (!split) {
      state = "non-split"
    }
    Log.i("Get Chrome: ${packageName}, v${packageInfo!!.versionName}, ${state}")
    setVersion()
  }

  fun getContext(): Context {
    return mContext!!.get()!!
  }

  private fun setVersion() {
    version = packageInfo!!.versionName.split(".").first().toInt()
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      longVersion = packageInfo!!.getLongVersionCode()
    } else {
      @Suppress("DEPRECATION")
      longVersion = packageInfo!!.versionCode.toLong()
    }
  }

  fun load(className: String): Class<*> {
    return getContext().getClassLoader().loadClass(className)
  }
}
