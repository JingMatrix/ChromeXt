package org.matrix.chromext.hook

// import org.matrix.chromext.utils.Log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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

    findMethod(proxy.intentHandler, true) {
          getParameterCount() == 3 &&
              getParameterTypes().first() == Context::class.java &&
              getParameterTypes().elementAt(1) == Intent::class.java &&
              getParameterTypes().last() == String::class.java
        }
        // private static void startActivityForTrustedIntentInternal(Context context, Intent
        // intent, String componentClassName)
        .hookBefore {
          val intent = it.args[1] as Intent
          if (intent.hasExtra("org.chromium.chrome.browser.customtabs.MEDIA_VIEWER_URL")) {
            val fileurl = convertDownloadUrl(it.args[0] as Context, intent.getData()!!)
            if (fileurl != null) {
              intent.setData(Uri.parse(fileurl))
            }
          }
        }
  }

  fun convertDownloadUrl(ctx: Context, uri: Uri): String? {
    var fileurl: String? = null
    ctx.getContentResolver().query(uri, null, null, null, null)?.use { cursor ->
      cursor.moveToFirst()
      val dataIndex = cursor.getColumnIndex("_data")
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      val filename = cursor.getString(nameIndex)
      if (dataIndex != -1 && filename.endsWith(".user.js")) {
        fileurl = "file://" + cursor.getString(dataIndex)
      }
    }
    return fileurl
  }
}
