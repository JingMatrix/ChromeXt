package org.matrix.chromext

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.matrix.chromext.utils.Log

const val TAG = "ChromeXt"

class OpenInChrome : Activity() {
  var defaultPackage = "com.android.chrome"

  fun invokeChromeTabbed(url: String) {
    val chromeMain =
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(defaultPackage, "com.google.android.apps.chrome.Main"))
    // Ensure that Chrome is started
    startActivity(chromeMain)
    startActivity(chromeMain.putExtra("ChromeXt", url))
  }

  @Suppress("QueryPermissionsNeeded")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    @Suppress("DEPRECATION")
    val installedApplications = getPackageManager().getInstalledApplications(0)
    val avaiblePackages = supportedPackages.intersect(installedApplications.map { it.packageName })
    if (avaiblePackages.size == 0) {
      Log.toast(this, "No supported Chrome installed")
      finish()
    } else {
      defaultPackage = avaiblePackages.last()
    }
    val intent: Intent = getIntent()
    val destination: ComponentName =
        ComponentName(defaultPackage, "com.google.android.apps.chrome.IntentDispatcher")
    if (intent.action == Intent.ACTION_VIEW) {
      intent.setComponent(destination)
      intent.setDataAndType(intent.getData(), "text/html")
      startActivity(intent)
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
        Log.d("Get share text: ${text}")
        if (text.startsWith("file://") || text.startsWith("data:")) {
          invokeChromeTabbed(text)
        } else if (text.startsWith("chrome://")) {
          // Unable to open chrome url
          Log.toast(this, "Unable to open chrome:// scheme")
        } else {
          if (!text.contains("://")) {
            text = "https://google.com/search?q=${text.replace("#", "%23")}"
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
