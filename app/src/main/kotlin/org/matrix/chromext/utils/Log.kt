package org.matrix.chromext.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.lang.ref.WeakReference
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.TAG

/**
 * Logging that works both inside the hooked browser and in ChromeXt's own process.
 *
 * `de.robv.android.xposed.XposedBridge` is a compile only dependency, so the host service cannot
 * touch it at all: reaching it there throws `NoClassDefFoundError` and takes the process down.
 */
private object XposedLog {
  private val available: Boolean = runCatching {
    Class.forName("de.robv.android.xposed.XposedBridge")
  }.isSuccess

  fun log(message: String) {
    if (!available) return
    runCatching { de.robv.android.xposed.XposedBridge.log(message) }
  }
}

object Log {
  private var lastToast: WeakReference<Toast>? = null

  fun i(msg: String) {
    Log.i(TAG, msg)
    XposedLog.log("ChromeXt logging: $msg")
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
    XposedLog.log("ChromeXt error: $msg")
  }

  fun ex(thr: Throwable, msg: String = "") {
    Log.e(TAG, msg, thr)
    XposedLog.log("ChromeXt exception caught: [$msg] $thr")
  }

  fun toast(context: Context, msg: String) {
    this.lastToast?.get()?.cancel()
    val duration = Toast.LENGTH_SHORT
    val toast = Toast.makeText(context, msg, duration)
    toast.show()
    this.lastToast = WeakReference(toast)
  }
}
