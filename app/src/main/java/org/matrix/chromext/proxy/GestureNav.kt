package org.matrix.chromext.proxy

import org.matrix.chromext.Chrome

class GestureNavProxy() {

  // The main class to hook is
  // org/chromium/chrome/browser/gesturenav/HistoryNavigationCoordinator.java

  private var HISTORY_NAVIGATION_COORDINATOR = "d31"
  // But we need several other classes to identify it
  // It has following fields:
  // private final Runnable mUpdateNavigationStateRunnable = this::onNavigationStateChanged;
  // private ViewGroup mParentView;
  // private HistoryNavigationLayout mNavigationLayout;
  // private InsetObserverView mInsetObserverView;
  // private CurrentTabObserver mCurrentTabObserver;
  // private ActivityLifecycleDispatcher mActivityLifecycleDispatcher;
  // private BackActionDelegate mBackActionDelegate;
  // private Tab mTab;
  // private boolean mEnabled;
  // Its last two fields are both Callback
  // We start with its HistoryNavigationLayout

  // private val HISTORY_NAVIGATION_LAYOUT = "g31"
  // which has two fields of Ljava/lang/Runnable,
  // one field of Lorg/chromium/base/Callback.
  // Note that it has the same two last letters as HistoryNavigationCoordinator
  // And it has a Field SideSlideLayout,
  // and if you grep with this field, there is only matching line

  // private val SIDE_SLIDE_LAYOUT = "vR2"
  // which can be identified with having many fields, such as
  // android/view/animation/Animation$AnimationListener,
  // android/view/animation/Animation,
  // android/view/animation/DecelerateInterpolator,
  // org/chromium/chrome/browser/gesturenav/NavigationBubble.

  // Once the class HistoryNavigationCoordinator is found, we care about its filed mEnabled
  // and its method isFeatureEnabled() with a Boolean return value
  val historyNavigationCoordinator: Class<*>
  // val ENABLE_FIELD = "s"
  var IS_FEATURE_ENABLED = "b"

  // val decorView: Class<*>? = null
  // val sideSlideLayout: Class<*>? = null
  val chromeTabbedActivity: Class<*>
  // Even though it exposes the onLayout method, it is not the correct Layout to hook

  // val UPDATE_NAVIGATION_HANDLER = "g"
  // identified since it contains WebContents class

  init {
    if (!Chrome.split) {
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

    if (Chrome.split && Chrome.version >= 111) {
      // private val SIDE_SLIDE_LAYOUT = "YM2"
      // private val HISTORY_NAVIGATION_LAYOUT = "RX0"
      HISTORY_NAVIGATION_COORDINATOR = "OX0"
    }

    historyNavigationCoordinator = Chrome.load(HISTORY_NAVIGATION_COORDINATOR)
    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
    // decorView = ctx.getClassLoader().loadClass("com.android.internal.policy.DecorView")
    // sideSlideLayout = ctx.getClassLoader().loadClass(SIDE_SLIDE_LAYOUT)
  }
}
