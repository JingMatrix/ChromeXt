package org.matrix.chromext.proxy

import org.matrix.chromext.Chrome

object PageMenuProxy {

  val chromeTabbedActivity = UserScriptProxy.chromeTabbedActivity
  val customTabActivity = Chrome.load("org.chromium.chrome.browser.customtabs.CustomTabActivity")
  val propertyModel = Chrome.load("org.chromium.ui.modelutil.PropertyModel")
  val tab = Chrome.load("org.chromium.chrome.browser.tab.Tab")
  val emptyTabObserver =
      Chrome.load("org.chromium.chrome.browser.login.ChromeHttpAuthHandler").superclass as Class<*>
  val tabImpl = UserScriptProxy.tabImpl
  val mIsLoading = UserScriptProxy.mIsLoading
}
