package org.matrix.chromext

import android.content.Context
import java.lang.ref.WeakReference

object Chrome {
  private var mContext: WeakReference<Context>? = null
  private var packageName: String? = null
  var split = true
  // Might be extended to support different versions

  fun init(ctx: Context, pack: String) {
    mContext = WeakReference(ctx)
    packageName = pack
  }

  fun getContext(): Context {
    return mContext!!.get()!!
  }

  fun getPackageName(): String {
    return packageName!!
  }

  fun load(className: String): Class<*> {
    return getContext().getClassLoader().loadClass(className)
  }
}
