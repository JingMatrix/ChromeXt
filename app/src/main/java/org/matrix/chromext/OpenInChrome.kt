package org.matrix.chromext

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle

private const val TAG = "ChromeXt"

class OpenInChrome : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val intent: Intent = getIntent()
    intent.setComponent(
        ComponentName("com.android.chrome", "com.google.android.apps.chrome.IntentDispatcher"))
    if (intent.action == Intent.ACTION_VIEW) {
      intent.setDataAndType(intent.getData(), "text/html")
      startActivity(intent)
    } else if (intent.action == Intent.ACTION_SEND) {
      var text = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (text != null) {
        if (!text.contains("://")) {
          text = "https://google.com/search?q=${text}"
        }
        startActivity(
            Intent().apply {
              action = Intent.ACTION_VIEW
              data = Uri.parse(text)
              component =
                  ComponentName(
                      "com.android.chrome", "com.google.android.apps.chrome.IntentDispatcher")
            })
      }
    }
    finish()
  }
}
