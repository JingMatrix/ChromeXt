package org.matrix.chromext.hook

// import org.matrix.chromext.utils.Log
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.matrix.chromext.convertDownloadUrl
import org.matrix.chromext.proxy.IntentProxy
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookBefore

object IntentHook : BaseHook() {
  override fun init() {

    val proxy = IntentProxy()

    findMethod(proxy.chromeTabbedActivity) { name == "onNewIntent" }
        .hookBefore {
          val intent = it.args[0] as Intent
          if (intent.hasExtra("ChromeXt")) {
            val url = intent.getStringExtra("ChromeXt") as String
            intent.setAction(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(url))
          }
        }

    findMethod(proxy.intentHandler) { name == proxy.START_ACTIVITY }
        // private static void startActivityForTrustedIntentInternal(Context context, Intent
        // intent, String componentClassName)
        .hookBefore {
          val intent = it.args[1] as Intent
          if (intent.hasExtra("org.chromium.chrome.browser.customtabs.MEDIA_VIEWER_URL")) {
            val fileurl = convertDownloadUrl(it.args[0] as Context, intent.getData()!!)
            if (fileurl != null) {
              intent.setData(Uri.parse(fileurl))
            } else {}
          }
        }
  }
}
