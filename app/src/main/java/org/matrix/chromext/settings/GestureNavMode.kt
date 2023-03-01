package org.matrix.chromext.settings

import android.content.Context
import android.view.View
import android.view.View.OnClickListener
import org.matrix.chromext.Chrome
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.utils.Log

object GestureNavMode : OnClickListener {

  override fun onClick(v: View) {
    val context = Chrome.getContext()
    val sharedPref =
        context.getSharedPreferences("com.android.chrome_preferences", Context.MODE_PRIVATE)
    with(sharedPref!!.edit()) {
      putBoolean("gesture_mod", !MenuProxy.getGestureMod())
      apply()
      Log.toast(context, "Please restart Chrome to apply the changes")
    }
  }
}
