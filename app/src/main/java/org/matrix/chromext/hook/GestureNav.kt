package org.matrix.chromext.hook

// import org.matrix.chromext.utils.Log
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import kotlin.math.roundToInt
import org.matrix.chromext.proxy.GestureNavProxy
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object GestureNavHook : BaseHook() {
  override fun init(ctx: Context, split: Boolean) {

    val proxy = GestureNavProxy(ctx, split)
    var activity: Activity? = null

    findMethod(proxy.historyNavigationCoordinator) { name == proxy.IS_FEATURE_ENABLED }
        // private boolean isFeatureEnabled()
        .hookBefore {
          excludeSystemGesture(activity!!)
          it.result = true
        }

    findMethod(proxy.chromeTabbedActivity) { name == "onStart" }
        .hookAfter { activity = it.thisObject as Activity }
  }

  fun excludeSystemGesture(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val decoView = activity.getWindow().getDecorView()
      val width = decoView.getWidth()
      val height = decoView.getHeight()
      val excludeHeight: Int =
          (activity.getResources().getDisplayMetrics().density * 100).roundToInt()
      Log.d("Called setSystemGestureExclusionRects with size ${width} x ${excludeHeight * 2}")
      decoView.setSystemGestureExclusionRects(
          // public Rect (int left, int top, int right, int bottom)
          listOf(Rect(width / 2, height / 2 - excludeHeight, width, height / 2 + excludeHeight)))
    }
  }
}
