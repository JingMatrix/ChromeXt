package org.matrix.chromext.hook

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import org.matrix.chromext.Chrome
import org.matrix.chromext.proxy.GestureNavProxy
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object GestureNavHook : BaseHook() {

  override fun init() {

    var activity: WeakReference<Activity>? = null
    val proxy = GestureNavProxy()

    findMethod(proxy.historyNavigationCoordinator) { name == proxy.IS_FEATURE_ENABLED }
        // private boolean isFeatureEnabled()
        .hookBefore {
          val sharedPref =
              Chrome.getContext()
                  .getSharedPreferences("com.android.chrome_preferences", Context.MODE_PRIVATE)
          if (sharedPref.getBoolean("gesture_mod", true)) {
            fixConflict(activity!!.get()!!)
            it.result = true
          }
        }

    findMethod(proxy.chromeTabbedActivity, true) { name == "onStart" }
        .hookAfter { activity = WeakReference(it.thisObject as Activity) }
  }

  fun fixConflict(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val decoView = activity.getWindow().getDecorView()
      val width = decoView.getWidth()
      val height = decoView.getHeight()
      val excludeHeight: Int =
          (activity.getResources().getDisplayMetrics().density * 100).roundToInt()
      // Log.d("Called setSystemGestureExclusionRects with size ${width} x ${excludeHeight * 2}")
      decoView.setSystemGestureExclusionRects(
          // public Rect (int left, int top, int right, int bottom)
          listOf(Rect(width / 2, height / 2 - excludeHeight, width, height / 2 + excludeHeight)))
    }
  }
}
