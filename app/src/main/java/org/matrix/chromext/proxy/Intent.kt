package org.matrix.chromext.proxy

import org.matrix.chromext.Chrome

class IntentProxy() {

  // Grep 'Ignoring internal Chrome URL from untrustworthy source.' to get the class
  // org/chromium/chrome/browser/IntentHandler.java
  private var INTENT_HANDLER = "ji1"
  // Grep (Landroid/content/Context;Landroid/content/Intent;Ljava/lang/String;)V
  // to get its method startActivityForTrustedIntentInternal
  var START_ACTIVITY = "z"

  // Use frida-ps to get the method getExtraHeadersFromIntent
  // that returns a header
  // private val GET_EXTRA_HEADERS = "f"

  val chromeTabbedActivity: Class<*>

  val intentHandler: Class<*>

  init {
    if (!Chrome.split) {
      INTENT_HANDLER = "Ot1"
    }

    if (!Chrome.split && Chrome.version >= 109) {
      INTENT_HANDLER = "gu1"
    }

    if (Chrome.split && Chrome.version >= 109) {
      INTENT_HANDLER = "xi1"
    }

    if (!Chrome.split && Chrome.version >= 110) {
      INTENT_HANDLER = "In1"
    }

    if (Chrome.split && Chrome.version >= 110) {
      INTENT_HANDLER = "cc1"
    }

    if (Chrome.split && Chrome.version >= 111) {
      INTENT_HANDLER = "yd1"
    }

    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
    intentHandler = Chrome.load(INTENT_HANDLER)
  }
}
