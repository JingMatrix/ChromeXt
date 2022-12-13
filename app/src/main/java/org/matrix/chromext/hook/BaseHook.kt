package org.matrix.chromext.hook

abstract class BaseHook {
  var isInit: Boolean = false
  abstract fun init()
  companion object {
    // The first way is to hook TabWebContentsDelegateAndroidImpl
    // We have less control but won't bother javascript: scheme

    // The only field with type Ljava/lang/String in
    // org/chromium/url/GURL.smali is for URL
    const val SPEC_FIELD = "a"
    // ! Note: GURL has limited length:
    // const size_t kMaxURLChars = 2 * 1024 * 1024 in chromium/src/ur/ url_constants.cc

    // Use frida-trace to find method for casting GURL to String in org/chromium/url/GURL.smali
    const val SHOW_URL = "j"
    // ! Note: This is not used since a method name is not stable comparing to a field name

    // Grep smali code with Tab.loadUrl to get the loadUrl function in
    // org/chromium/chrome/browser/tab/TabImpl.smali
    const val LOAD_URL = "h"

    // Get TabImpl field in
    // org/chromium/chrome/browser/tab/TabWebContentsDelegateAndroidImpl.smali
    const val TAB_FIELD = "a"

    // Another way is to hook a NavigationControllerImpl
    // ! Note: loadUrl is only called for browser-Initiated navigations

    // The first filed of org/chromium/content_public/browser/LoadUrlParams should
    // be the mUrl
    const val URL_FIELD = "a"

    // It is possible to a HTTP POST with LoadUrlParams Class
    // Grep org/chromium/content_public/common/ResourceRequestBody to get setPostData in
    // org/chromium/content_public/browser/LoadUrlParams

    const val POST_DATA = "b"
    // One can also directly change fields using refelection,
    // ! Note: this is very POWERFUL

    // If we starts with a WebContentsImpl, then
    // grep ()Lorg/chromium/content_public/browser/NavigationController
    // to get NavigationController in
    // org/chromium/content/browser/webcontents/WebContentsImpl.smali
    const val NAVI_CONTROLLER = "i"
    // ! Note: currently this way is not applied in our code

    // We can also directly hook NavigationController
    // Grep Android.Omnibox.InputToNavigationControllerStart to get loadUrl in
    // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
    const val NAVI_LOAD_URL = "h"

    // Grep ()I to get or goToNavigationIndex in
    // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
    // Current tab has the biggest index, a new tab has index 0, index is stored with tab
    const val NAVI_GOTO = "z"

    // Grep (I)V to get or getLastCommittedEntryIndex in
    // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
    // Current tab has the biggest index, a new tab has index 0, index is stored with tab
    const val NAVI_LAST_INDEX = "e"

    // Grep Android.Intent.IntentUriNavigationResult to get class
    // org/chromium/components/external_intents/ExternalNavigationHandler.java
    const val EXTERNALNAVIGATIONHANDLER = "GC0"

    // Grep .super Lorg/chromium/components/navigation_interception/InterceptNavigationDelegate
    // to get class org.chromium.components.external_intents.InterceptNavigationDelegateImpl
    const val INTERCEPTNAVIGATIONDELEGATEIMPL = "yi1"

    // Grep (Lorg/chromium/content_public/browser/WebContents;)V
    // in INTERCEPTNAVIGATIONDELEGATEIMPL to get associateWithWebContents
    const val ASSOCIATE_CONTENTS = "a"
    // ! Note: not using yet, might be useful in the future
  }
}
