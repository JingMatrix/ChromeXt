package org.matrix.chromext.proxy

import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.findFieldOrNull

object PageMenuProxy {

  val chromeTabbedActivity = UserScriptProxy.chromeTabbedActivity
  val customTabActivity = Chrome.load("org.chromium.chrome.browser.customtabs.CustomTabActivity")
  val propertyModel = Chrome.load("org.chromium.ui.modelutil.PropertyModel")
  val tab = Chrome.load("org.chromium.chrome.browser.tab.Tab")
  val emptyTabObserver =
      Chrome.load("org.chromium.chrome.browser.login.ChromeHttpAuthHandler").superclass as Class<*>
  val tabImpl = UserScriptProxy.tabImpl
  // TabImpl.mObservers is the only ObserverList it holds; only reader mode needs it, so a miss must
  // not blow up this initializer and take PageMenuHook down with it.
  val mObservers = findFieldOrNull(tabImpl) { type.interfaces.contains(Iterable::class.java) }
}
