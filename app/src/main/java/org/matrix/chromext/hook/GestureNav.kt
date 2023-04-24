package org.matrix.chromext.hook

import android.app.Activity
import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object GestureNavHook : BaseHook() {

  override fun init() {

    var activity: WeakReference<Activity>? = null

    findMethod(Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity"), true) {
          name == "onStart"
        }
        .hookAfter { activity = WeakReference(it.thisObject as Activity) }

    findMethod(WindowInsets::class.java) { name == "getSystemGestureInsets" }
        .hookBefore {
          val sharedPref =
              Chrome.getContext()
                  .getSharedPreferences(
                      Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
          if (sharedPref.getBoolean("gesture_mod", true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              it.result = Insets.of(0, 0, 0, 0)
            }
            fixConflict(activity?.get(), true)
          } else {
            fixConflict(activity?.get(), false)
          }
        }
  }

  fun fixConflict(activity: Activity?, excludeSystemGesture: Boolean) {
    if (activity == null) {
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val decoView = activity.getWindow().getDecorView()
      val width = decoView.getWidth()
      val height = decoView.getHeight()
      val excludeHeight: Int =
          (activity.getResources().getDisplayMetrics().density * 100).roundToInt()
      if (excludeSystemGesture) {
        decoView.setSystemGestureExclusionRects(
            // public Rect (int left, int top, int right, int bottom)
            listOf(Rect(width / 2, height / 2 - excludeHeight, width, height / 2 + excludeHeight)))
      } else {
        decoView.setSystemGestureExclusionRects(listOf(Rect(0, 0, 0, 0)))
      }
    }
  }
}
