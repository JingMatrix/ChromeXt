package org.matrix.chromext.hook

abstract class BaseHook {
  var isInit: Boolean = false
  abstract fun init()
  companion object {
    // The first way is to hook TabWebContentsDelegateAndroidImpl
    // We have less control but won't bother javascript: scheme

    // Use frida-trace to find method for casting GURL to String in org/chromium/url/GURL.smali
    const val SHOW_URL = "j"
    // grep smali code with Tab.loadUrl to get the loadUrl function in
    // org/chromium/chrome/browser/tab/TabImpl.smali
    const val LOAD_URL = "h"
    // get TabImpl field in
    // org/chromium/chrome/browser/tab/TabWebContentsDelegateAndroidImpl.smali
    const val TAB_FIELD = "a"

    // Another way is to hook a NavigationControllerImpl
    // But loadUrl is only called by new tab action or address url input

    // The first filed of org/chromium/content_public/browser/LoadUrlParams should
    // be the mUrl
    const val URL_FIELD = "a"
    // It is possible to a HTTP POST with LoadUrlParams Class
    // grep org/chromium/content_public/common/ResourceRequestBody to get setPostData in
    // org/chromium/content_public/browser/LoadUrlParams
    const val POST_DATA = "b"
    // One can also directly change fields using refelection,
    // this is very POWERFUL

    // If we starts with a WebContentsImpl, then
    // grep ()Lorg/chromium/content_public/browser/NavigationController
    // to get NavigationController in
    // org/chromium/content/browser/webcontents/WebContentsImpl.smali
    const val NAVI_CONTROLLER = "i"

    // We can also directly hook NavigationController
    // grep Android.Omnibox.InputToNavigationControllerStart to get loadUrl in
    // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
    const val NAVI_LOAD_URL = "h"
    // grep ()I to get or goToNavigationIndex in
    // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
    // Current tab has the biggest index, a new tab has index 0, index is stored with tab
    const val NAVI_GOTO = "z"
    // grep (I)V to get or getLastCommittedEntryIndex in
    // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
    // Current tab has the biggest index, a new tab has index 0, index is stored with tab
    const val NAVI_LAST_INDEX = "e"
  }
}
