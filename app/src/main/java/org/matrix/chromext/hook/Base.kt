package org.matrix.chromext.hook

import android.content.Context

abstract class BaseHook {
  var isInit: Boolean = false
  abstract fun init(ctx: Context)
}
