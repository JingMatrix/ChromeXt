package org.matrix.chromext.settings

import android.content.Context
import android.view.View
import android.view.View.OnClickListener
import org.matrix.chromext.utils.Log

class ExitDevMode(ctx: Context) : OnClickListener {

  var context: Context? = null

  init {
    context = ctx
  }

  override fun onClick(v: View) {
    val sharedPref =
        context!!.getSharedPreferences("com.android.chrome_preferences", Context.MODE_PRIVATE)
    with(sharedPref!!.edit()) {
      putBoolean("developer", false)
      apply()
      Log.toast(context!!, "Please restart Chrome to apply the changes")
    }
  }
}
