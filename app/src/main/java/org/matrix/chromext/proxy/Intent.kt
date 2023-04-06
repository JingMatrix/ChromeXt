package org.matrix.chromext.proxy

import org.matrix.chromext.Chrome

class IntentProxy() {

  val chromeTabbedActivity: Class<*>

  val intentHandler: Class<*>

  init {

    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")

    intentHandler =
        // Grep 'Ignoring internal Chrome URL from untrustworthy source.' to get the class
        // org/chromium/chrome/browser/IntentHandler.java
        Chrome.load("org.chromium.chrome.browser.app.ChromeActivity")
            .getDeclaredFields()
            .filter { it.toString().startsWith("public final ") }
            .last()
            .getType()
  }
}
