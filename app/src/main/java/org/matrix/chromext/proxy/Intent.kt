package org.matrix.chromext.proxy

import android.util.Pair
import java.lang.reflect.Modifier
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findFieldOrNull

object IntentProxy {

  val chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
  val intentHandler =
      // Grep 'Ignoring internal Chrome URL from untrustworthy source.' to get the class
      // org/chromium/chrome/browser/IntentHandler.java
      findField(Chrome.load("org.chromium.chrome.browser.app.ChromeActivity")) {
            Modifier.isFinal(getModifiers()) &&
                findFieldOrNull(type, true) { type == Pair::class.java } != null
          }
          .type
}
