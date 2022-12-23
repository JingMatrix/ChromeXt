package org.matrix.chromext

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log

private const val TAG = "ChromeXt"

class OpenInChrome : Activity() {

  fun invokeChromeTabbed(url: String) {
    val chromeMain =
        Intent(Intent.ACTION_MAIN)
            .setComponent(
                ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main"))
    // Ensure that Chrome is started
    startActivity(chromeMain)
    startActivity(chromeMain.putExtra("ChromeXt", url))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val intent: Intent = getIntent()
    val destination: ComponentName =
        ComponentName("com.android.chrome", "com.google.android.apps.chrome.IntentDispatcher")
    if (intent.action == Intent.ACTION_VIEW) {
      intent.setComponent(destination)
      val fileUrl = convertDownloadUrl(this, intent.getData()!!)
      if (fileUrl == null) {
        // intent.setDataAndType(intent.getData(), "text/html")
        startActivity(intent)
      } else {
        invokeChromeTabbed(fileUrl)
      }
    } else if (intent.action == Intent.ACTION_SEND) {
      var text = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (text != null && intent.getType() == "text/plain") {
        text = text.trim()
        if (text.contains("\n ")) {
          val shareLink = text.split("\n ")[1]
          if (shareLink.contains("://")) {
            text = shareLink
          }
        }
        Log.d(TAG, "Get share text: ${text}")
        if (text.startsWith("file://") ||
            // Not able to open chrome url
            // text.startsWith("chrome://") ||
            text.startsWith("data:")) {
          invokeChromeTabbed(text)
        } else {
          if (!text.contains("://")) {
            text = "https://google.com/search?q=${text}"
          }
          startActivity(
              Intent().apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(text)
                component = destination
              })
        }
      }
    }
    finish()
  }
}

fun convertDownloadUrl(ctx: Context, uri: Uri): String? {
  if (uri.toString().startsWith("file://")) {
    return null
  }
  val url = uri.getPath()!!
  if (url.startsWith("/external/downloads")) {
    ctx.getContentResolver().query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      cursor.moveToFirst()
      val filename = cursor.getString(nameIndex)
      return "file://" +
          Environment.getExternalStorageDirectory().getPath() +
          "/" +
          Environment.DIRECTORY_DOWNLOADS +
          "/" +
          filename
    }
  }
  return null
}
