package org.matrix.chromext.proxy

import android.util.Pair
import java.lang.reflect.Modifier
import org.matrix.chromext.Chrome

object IntentProxy {

  val chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
  val intentHandler =
      // Grep 'Ignoring internal Chrome URL from untrustworthy source.' to get the class
      // org/chromium/chrome/browser/IntentHandler.java
      Chrome.load("org.chromium.chrome.browser.app.ChromeActivity")
          .getDeclaredFields()
          .find {
            Modifier.isFinal(it.getModifiers()) &&
                (it.getType().getDeclaredFields().find { it.getType() == Pair::class.java } !=
                    null ||
                    it.getType().getSuperclass().getDeclaredFields().find {
                      it.getType() == Pair::class.java
                    } != null)
          }!!
          .getType()
}
