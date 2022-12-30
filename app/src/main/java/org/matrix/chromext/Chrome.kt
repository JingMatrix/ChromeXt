package org.matrix.chromext

import android.content.Context
import android.content.pm.PackageInfo
import java.lang.ref.WeakReference
import org.matrix.chromext.utils.Log

object Chrome {
  private var mContext: WeakReference<Context>? = null
  var packageInfo: PackageInfo? = null
  var split = true
  // Might be extended to support different versions

  fun init(ctx: Context) {
    mContext = WeakReference(ctx)
    val packageName = ctx.getPackageName()
    packageInfo = ctx.getPackageManager().getPackageInfo(packageName, 0)
    var state = "split"
    if (!split) {
      state = "non-split"
    }
    Log.i("Get Chrome: ${packageName}, v${packageInfo!!.versionName}, ${state}")
  }

  fun getContext(): Context {
    return mContext!!.get()!!
  }

  fun load(className: String): Class<*> {
    return getContext().getClassLoader().loadClass(className)
  }
}
