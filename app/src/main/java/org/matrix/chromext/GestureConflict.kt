package org.matrix.chromext

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import kotlin.math.roundToInt
import org.matrix.chromext.utils.Log

object GestureConflict {

  var activity: Activity? = null

  fun hookActivity(a: Activity) {
    activity = a
  }

  fun fix() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val decoView = activity!!.getWindow().getDecorView()
      val width = decoView.getWidth()
      val height = decoView.getHeight()
      val excludeHeight: Int =
          (activity!!.getResources().getDisplayMetrics().density * 100).roundToInt()
      Log.d("Called setSystemGestureExclusionRects with size ${width} x ${excludeHeight * 2}")
      decoView.setSystemGestureExclusionRects(
          // public Rect (int left, int top, int right, int bottom)
          listOf(Rect(width / 2, height / 2 - excludeHeight, width, height / 2 + excludeHeight)))
    }
  }
}
