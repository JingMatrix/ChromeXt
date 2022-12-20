package org.matrix.chromext.hook

import android.content.Context
import android.content.Intent
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore

object IntentHook : BaseHook() {
  override fun init(ctx: Context) {
    findMethod(ctx.getClassLoader().loadClass("org.chromium.chrome.browser.ChromeTabbedActivity")) {
          name == "onNewIntent"
        }
        .hookBefore {
          val intent = it.args[0] as Intent
          if (intent.hasExtra("ChromeXt")) {
            Log.i(intent.getStringExtra("ChromeXt") as String)
          }
        }
  }
}
