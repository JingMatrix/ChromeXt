package org.matrix.chromext.settings

import android.content.Context
import android.view.View
import android.view.View.OnClickListener
import android.widget.Toast

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
      val text = "Please restart Chrome to apply the changes"
      val duration = Toast.LENGTH_SHORT
      val toast = Toast.makeText(context, text, duration)
      toast.show()
    }
  }
}
