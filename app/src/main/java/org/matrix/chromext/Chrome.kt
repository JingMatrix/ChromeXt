package org.matrix.chromext

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import android.os.Process
import java.lang.ref.WeakReference
import org.matrix.chromext.utils.Log

object Chrome {
  private var mContext: WeakReference<Context>? = null
  private var packageInfo: PackageInfo? = null
  private var longVersion: Long = 0
  var isDev = false
  var version = 0
  var isEdge = false
  var isVivaldi = false

  fun init(ctx: Context) {
    mContext = WeakReference(ctx)
    ResourceMerge.enrich(ctx)

    if (version != 0) {
      // Switching color theme will construct new WindowAndroid instance
      return
    }
    val packageName = ctx.getPackageName()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      packageInfo = ctx.getPackageManager().getPackageInfo(packageName, PackageInfoFlags.of(0))
    } else {
      @Suppress("DEPRECATION")
      packageInfo = ctx.getPackageManager().getPackageInfo(packageName, 0)
    }
    isDev = packageName.endsWith("canary") || packageName.endsWith("dev")
    if (packageName.startsWith("com.microsoft.emmx")) {
      isEdge = true
    }
    if (packageName == "com.vivaldi.browser") {
      isVivaldi = true
      isDev = true
    }
    Log.i("Package: ${packageName}, v${packageInfo!!.versionName}, pid ${Process.myPid()}")
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
