package org.matrix.chromext

import android.content.Context

object Chrome {
  private var mContext: Context? = null
  private var packageName: String? = null
  var split = true
  // Might be extended to support different versions

  fun init(ctx: Context, pack: String) {
    mContext = ctx
    packageName = pack
  }

  fun getContext(): Context {
    return mContext!!
  }

  fun getPackageName(): String {
    return packageName!!
  }

  fun load(className: String): Class<*> {
    return mContext!!.getClassLoader().loadClass(className)
  }
}
