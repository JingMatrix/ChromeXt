package org.matrix.chromext.utils

import android.util.Log

const val TAG = "ChromeXt"

object Log {
  fun i(msg: String) {
    Log.i(TAG, msg)
  }

  fun d(msg: String) {
    Log.d(TAG, msg)
  }

  fun w(msg: String) {
    Log.w(TAG, msg)
  }

  fun e(msg: String) {
    Log.e(TAG, msg)
  }

  fun ex(thr: Throwable) {
    Log.e(TAG, thr.toString())
  }
}
