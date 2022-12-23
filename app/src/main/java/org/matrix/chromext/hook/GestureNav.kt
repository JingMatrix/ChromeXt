package org.matrix.chromext.hook

// import android.graphics.Rect
// import android.os.Build
// import android.view.View
// import org.matrix.chromext.utils.Log
import android.content.Context
import org.matrix.chromext.proxy.GestureNavProxy
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter

// import org.matrix.chromext.utils.hookBefore

object GestureNavHook : BaseHook() {
  override fun init(ctx: Context) {

    val proxy = GestureNavProxy(ctx)

    findMethod(proxy.historyNavigationCoordinator!!) { name == proxy.IS_FEATURE_ENABLED }
        // private boolean isFeatureEnabled()
        .hookAfter { it.result = true }

    // disableSystemGesture(proxy)
  }

  // fun disableSystemGesture(proxy: GestureNavProxy) {
  //   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
  //     findMethod(proxy.sideSlideLayout!!) { name == "onLayout" }
  //         // protected void onLayout(boolean changed, int left, int top, int right, int bottom)
  //         .hookAfter {
  //           val view = it.thisObject as View
  //           val left = it.args[1] as Int
  //           val top = it.args[2] as Int
  //           val right = it.args[3] as Int
  //           val bottom = it.args[4] as Int
  //           Log.i("Called setSystemGestureExclusionRects")
  //           view.setSystemGestureExclusionRects(
  //               // public Rect (int left, int top, int right, int bottom)
  //               listOf(Rect(left, top, right, bottom)))
  //         }
  //   }
  // }
}
