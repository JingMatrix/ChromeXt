package org.matrix.chromext.hook

import android.content.Context
import org.matrix.chromext.proxy.JniProxy

object JniHook : BaseHook() {
  override fun init(ctx: Context) {
    JniProxy(ctx)
  }
}
