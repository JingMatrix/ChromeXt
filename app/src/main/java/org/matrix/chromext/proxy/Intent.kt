package org.matrix.chromext.proxy

import android.content.Context

class IntentProxy(ctx: Context) {

  // Grep 'Ignoring internal Chrome URL from untrustworthy source.' to get the class
  // org/chromium/chrome/browser/IntentHandler.java
  private var INTENT_HANDLER = "ji1"

  // Grep (Landroid/content/Context;Landroid/content/Intent;Ljava/lang/String;)V
  // to get its method startActivityForTrustedIntentInternal
  var START_ACTIVITY = "z"

  // Use frida-ps to get the method getExtraHeadersFromIntent
  // that returns a header
  private val GET_EXTRA_HEADERS = "f"

  var chromeTabbedActivity: Class<*>? = null

  var intentHandler: Class<*>? = null

  init {
    chromeTabbedActivity =
        ctx.getClassLoader().loadClass("org.chromium.chrome.browser.ChromeTabbedActivity")
    intentHandler = ctx.getClassLoader().loadClass(INTENT_HANDLER)
  }
}
