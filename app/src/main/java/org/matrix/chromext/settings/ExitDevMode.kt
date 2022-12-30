package org.matrix.chromext.settings

import android.content.Context
import android.view.View
import android.view.View.OnClickListener
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

object ExitDevMode : OnClickListener {

  override fun onClick(v: View) {
    val context = Chrome.getContext()
    if (Chrome.isDev) {
      Log.toast(context, "This function is not available for your Chrome build")
      return
    }
    val sharedPref =
        context.getSharedPreferences("com.android.chrome_preferences", Context.MODE_PRIVATE)
    with(sharedPref!!.edit()) {
      putBoolean("developer", false)
      apply()
      Log.toast(context, "Please restart Chrome to apply the changes")
    }
  }
}
