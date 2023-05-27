package org.matrix.chromext

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast

const val TAG = "ChromeXt"

val supportedPackages =
    arrayOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.vivaldi.browser",
        "com.vivaldi.browser.snapshot",
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.dev",
        "com.microsoft.emmx.canary",
        "org.bromite.bromite",
        "org.chromium.thorium",
        "us.spotco.mulch",
        "com.brave.browser",
        "com.brave.browser_beta")

fun toast(context: Context, msg: String) {
  val duration = Toast.LENGTH_SHORT
  val toast = Toast.makeText(context, msg, duration)
  toast.show()
}

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
      toast(this, "No supported Chrome installed")
      finish()
      return
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
      if (text == null || intent.getType() != "text/plain") {
        finish()
        return
      }

      Log.d(TAG, "Get share text: ${text}")
      if (text.startsWith("file://") || text.startsWith("data:")) {
        invokeChromeTabbed(text)
      } else {
        if (!text.contains("://")) {
          text = "https://google.com/search?q=${text.replace("#", "%23")}"
        } else if (text.contains("\n ")) {
          text = text.split("\n ")[1]
        }

        if (!text.startsWith("http")) {
          // Unable to open custom url
          toast(this, "Unable to open ${text.split("://").first()} scheme")
          finish()
          return
        }

        startActivity(
            Intent().apply {
              action = Intent.ACTION_VIEW
              data = Uri.parse(text)
              component = destination
            })
      }
    }
    finish()
  }
}
