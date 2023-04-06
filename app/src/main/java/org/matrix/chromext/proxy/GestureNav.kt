package org.matrix.chromext.proxy

import org.matrix.chromext.Chrome

class GestureNavProxy() {

  // The main class to hook is
  // org/chromium/chrome/browser/gesturenav/HistoryNavigationCoordinator.java
  // To find it, we need to find out other two classes

  private var HISTORY_NAVIGATION_COORDINATOR = "d31"
  // It has filed HistoryNavigationLayout
  // with 3 matches (a 15th and two 470th lines)
  // Moreover, its name has the same two last letters as HistoryNavigationCoordinator

  // private val HISTORY_NAVIGATION_LAYOUT = "g31"
  // It field SideSlideLayout with exactly one match around the 16th line
  // It has two fields of Ljava/lang/Runnable,
  // one field of Lorg/chromium/base/Callback.

  // private val SIDE_SLIDE_LAYOUT = "vR2"
  // It has field org/chromium/chrome/browser/gesturenav/NavigationBubble
  // around the 43rd line; it also has other fileds
  // android/view/animation/Animation$AnimationListener,
  // android/view/animation/Animation,
  // android/view/animation/DecelerateInterpolator,

  // Once the class HistoryNavigationCoordinator is found, we care about its filed mEnabled
  // and its method isFeatureEnabled() with a Boolean return value
  val historyNavigationCoordinator: Class<*>
  // val ENABLE_FIELD = "s"
  var IS_FEATURE_ENABLED = "b"

  val chromeTabbedActivity: Class<*>
  // Even though it exposes the onLayout method, it is not the correct Layout to hook

  init {
    if (Chrome.split && Chrome.version == 103) {
      // private val SIDE_SLIDE_LAYOUT = "L43"
      // private val HISTORY_NAVIGATION_LAYOUT = "ma1"
      HISTORY_NAVIGATION_COORDINATOR = "ia1"
    }

    if (!Chrome.split && Chrome.version >= 108) {
      // private val SIDE_SLIDE_LAYOUT = "E83"
      // private val HISTORY_NAVIGATION_LAYOUT = "Xb1"
      HISTORY_NAVIGATION_COORDINATOR = "Ub1"
    }

    if (!Chrome.split && Chrome.version >= 109) {
      // private val SIDE_SLIDE_LAYOUT = "w93"
      // private val HISTORY_NAVIGATION_LAYOUT = "Ic1"
      HISTORY_NAVIGATION_COORDINATOR = "Fc1"
    }

    if (Chrome.split && Chrome.version >= 109) {
      // private val SIDE_SLIDE_LAYOUT = "mS2"
      // private val HISTORY_NAVIGATION_LAYOUT = "K31"
      HISTORY_NAVIGATION_COORDINATOR = "H31"
    }

    if (!Chrome.split && Chrome.version >= 110) {
      // private val SIDE_SLIDE_LAYOUT = "J23"
      // private val HISTORY_NAVIGATION_LAYOUT = "b61"
      HISTORY_NAVIGATION_COORDINATOR = "Y51"
    }

    if (Chrome.split && Chrome.version >= 110) {
      // private val SIDE_SLIDE_LAYOUT = "zL2"
      // private val HISTORY_NAVIGATION_LAYOUT = "gX0"
      HISTORY_NAVIGATION_COORDINATOR = "dX0"
    }

    if (!Chrome.split && Chrome.version >= 111) {
      // private val SIDE_SLIDE_LAYOUT = "W43"
      // private val HISTORY_NAVIGATION_LAYOUT = "z71"
      HISTORY_NAVIGATION_COORDINATOR = "w71"
    }

    if (Chrome.split && Chrome.version >= 111) {
      // private val SIDE_SLIDE_LAYOUT = "BN2"
      // private val HISTORY_NAVIGATION_LAYOUT = "zY0"
      HISTORY_NAVIGATION_COORDINATOR = "wY0"
    }

    historyNavigationCoordinator = Chrome.load(HISTORY_NAVIGATION_COORDINATOR)
    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
  }
}
