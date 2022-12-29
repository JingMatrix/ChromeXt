package org.matrix.chromext.hook

// import org.matrix.chromext.utils.Log

import android.content.Context
import org.matrix.chromext.GestureConflict
import org.matrix.chromext.proxy.GestureNavProxy
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookBefore

object GestureNavHook : BaseHook() {
  override fun init(ctx: Context, split: Boolean) {

    val proxy = GestureNavProxy(ctx, split)

    findMethod(proxy.historyNavigationCoordinator) { name == proxy.IS_FEATURE_ENABLED }
        // private boolean isFeatureEnabled()
        .hookBefore {
          GestureConflict.fix()
          it.result = true
        }
  }
}
