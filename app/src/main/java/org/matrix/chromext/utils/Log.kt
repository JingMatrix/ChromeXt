package org.matrix.chromext.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.TAG

object Log {
  fun i(msg: String) {
    Log.i(TAG, msg)
    XposedBridge.log("ChromeXt logging: " + msg)
  }

  fun d(msg: String, full: Boolean = false) {
    if (BuildConfig.DEBUG) {
      if (!full && msg.length > 300) {
        Log.d(TAG, msg.take(300) + " ...")
      } else {
        Log.d(TAG, msg)
      }
    }
  }

  fun w(msg: String) {
    Log.w(TAG, msg)
  }

  fun e(msg: String) {
    Log.e(TAG, msg)
    XposedBridge.log("ChromeXt error: " + msg)
  }

  fun ex(thr: Throwable) {
    Log.e(TAG, "", thr)
    XposedBridge.log("ChromeXt backtrace: " + thr.toString())
  }

  fun toast(context: Context, msg: String) {
    val duration = Toast.LENGTH_SHORT
    val toast = Toast.makeText(context, msg, duration)
    toast.show()
  }
}
