package org.matrix.chromext.hook

import android.content.Context
import android.content.Intent
import android.net.Uri
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
            val url = intent.getStringExtra("ChromeXt") as String
            intent.setAction(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(url))
          }
        }

    // // org.chromium.chrome.browser.IntentHandler
    // findMethod(ctx.getClassLoader().loadClass("ji1")) { name == "z" }
    //     // private static void startActivityForTrustedIntentInternal(Context context, Intent
    // intent,
    //     // String componentClassName)
    //     .hookBefore {
    //       val intent = it.args[1] as Intent
    //       Log.d(intent.toUri(0))
    //     }

    // findMethod(ctx.getClassLoader().loadClass("ji1")) { name == "f" }
    // // public static String getExtraHeadersFromIntent(Intent intent)
    //     .hookBefore {
    //       val intent = it.args[0] as Intent
    //       Log.d(intent.toUri(0))
    // // it.setResult("Content-Type: text/javascript;charset=utf-8")
    //     }

  }
}
