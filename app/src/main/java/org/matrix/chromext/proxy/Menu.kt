package org.matrix.chromext.proxy

import android.content.Context
import java.lang.reflect.Field
import kotlin.text.Regex
import org.matrix.chromext.Chrome
import org.matrix.chromext.settings.DownloadEruda
import org.matrix.chromext.settings.ExitDevMode
import org.matrix.chromext.utils.invokeMethod

class MenuProxy() {
  // Grep DomDistiller.InfoBarUsage to get the class
  // ReaderModeManager with it method activateReaderMode
  // One get two methods, the other one is onClosed, which has
  // much shorter implements
  var READER_MODE_MANAGER = "js2"
  var ACTIVATE_READER_MODE = "Y0"
  // Get its current tab field
  var TAB_FIELD = "v"
  // Also we need its gURL field
  var DISTILLER_URL_FIELD = "p"

  // Grep Android.PrepareMenu.OpenWebApkVisibilityCheck to get the class
  // org/chromium/chrome/browser/app/appmenu/AppMenuPropertiesDelegateImpl.java
  var APP_MENU_PROPERTIES_DELEGATE_IMPL = "of"

  // Grep (Landroid/view/Menu;Lorg/chromium/chrome/browser/tab/Tab;ZZ)V
  // to get method updateRequestDesktopSiteMenuItem of AppMenuPropertiesDelegateImpl
  var UPDATE_REQUEST_DESKTOP_SITE_MENU_ITEM = "q"

  // Grep MobileMenuSettings to get method onMenuOrKeyboardAction
  // in the class ChromeTabbedActivity.smali
  var MENU_KEYBOARD_ACTION = "h0"

  // Find the super class PreferenceFragmentCompat of DeveloperSettings.smali
  var PREFERENCE_FRAGMENT_COMPAT = "pk2"

  // Grep (I)V to get method addPreferencesFromResource
  // in the class PreferenceFragmentCompat
  var ADD_PREFERENCES_FROM_RESOURCE = "T0"

  // NOT NEEDED FOR SOME SPLIT VERSION
  // Inside the smali code of this method,
  // we see its super class androidx/fragment/app/c
  // has a method getContext()
  var GET_CONTEXT = "H0"

  // Grep (Ljava/lang/CharSequence;)Landroidx/preference/Preference;
  // to get method findPreference of PreferenceFragmentCompat
  var FIND_PREFERENCE = "U0"

  // Grep "Preference already has a SummaryProvider set"
  // to get method setSummary of Preference
  // and the corresponding field
  var SET_SUMMARY = "Q"

  // Find field with Landroid/view/View$OnClickListener
  var CLICK_LISTENER_FIELD = "X"

  // Grep ()Landroidx/preference/PreferenceScreen to get method getPreferenceScreen
  // in the class PreferenceFragmentCompat
  // val GET_PREFERENCE_SCREEN = "V0"

  // Compare method code length in Preference.smali,
  // the shortest one with a unknown class should be
  // setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener)
  // We should use its field instead of method
  // val ON_PREFERENCE_CLICK_LISTENER = "yk2"
  // val PREFERENCE_CLICK_LISTENER_FIELD = "l"

  companion object {
    fun getErudaVersion(): String? {
      val sharedPref = Chrome.getContext().getSharedPreferences("Eruda", Context.MODE_PRIVATE)
      if (!sharedPref.contains("eruda")) {
        return null
      }
      var eruda = sharedPref.getString("eruda", "")
      if (eruda == "") {
        return null
      } else {
        if (eruda!!.length > 200) {
          eruda = eruda.take(150)
        }
        val verisonReg = Regex("""/npm/eruda@(?<version>[\d\.]+)/eruda""")
        val vMatchGroup = verisonReg.find(eruda)?.groups as? MatchNamedGroupCollection
        if (vMatchGroup != null) {
          return vMatchGroup.get("version")?.value as String
        }
        return "unknown"
      }
    }
  }

  val chromeTabbedActivity: Class<*>
  val appMenuPropertiesDelegateImpl: Class<*>
  val developerSettings: Class<*>
  val preferenceFragmentCompat: Class<*>
  val readerModeManager: Class<*>
  val gURL: Class<*>
  // val onPreferenceClickListener: Class<*>? = null

  private val preference: Class<*>
  private val mClickListener: Field
  val mDistillerUrl: Field
  val mTab: Field
  // private val mOnClickListener: Field? = null

  var isDeveloper: Boolean = false

  init {
    if (!Chrome.split) {
      READER_MODE_MANAGER = "bH2"
      TAB_FIELD = "p"
      DISTILLER_URL_FIELD = "j"
      ACTIVATE_READER_MODE = "Z0"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "lg"
      MENU_KEYBOARD_ACTION = "v0"
      GET_CONTEXT = "I0"
      PREFERENCE_FRAGMENT_COMPAT = "Ey2"
      SET_SUMMARY = "P"
      CLICK_LISTENER_FIELD = "R"
    }

    if (Chrome.split && Chrome.version >= 109) {
      READER_MODE_MANAGER = "Ts2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "tf"
      MENU_KEYBOARD_ACTION = "i0"
      PREFERENCE_FRAGMENT_COMPAT = "al2"
      ADD_PREFERENCES_FROM_RESOURCE = "U0"
      GET_CONTEXT = "I0"
      FIND_PREFERENCE = "V0"
      SET_SUMMARY = "R"
    }

    if (Chrome.split && Chrome.version >= 110) {
      READER_MODE_MANAGER = "nm2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "pf"
      MENU_KEYBOARD_ACTION = "h0"
      PREFERENCE_FRAGMENT_COMPAT = "je2"
      ADD_PREFERENCES_FROM_RESOURCE = "T0"
      GET_CONTEXT = "I0"
      FIND_PREFERENCE = "U0"
      SET_SUMMARY = "R"
    }

    if (Chrome.split && Chrome.version >= 111) {
      READER_MODE_MANAGER = "Cn2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Mf"
      MENU_KEYBOARD_ACTION = "h0"
      PREFERENCE_FRAGMENT_COMPAT = "zf2"
      ADD_PREFERENCES_FROM_RESOURCE = "U0"
      GET_CONTEXT = "J0"
      FIND_PREFERENCE = "V0"
      SET_SUMMARY = "R"
    }

    val sharedPref =
        Chrome.getContext()
            .getSharedPreferences(
                Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
    isDeveloper = sharedPref!!.getBoolean("developer", false)

    preference = Chrome.load("androidx.preference.Preference")
    // onPreferenceClickListener = Chrome.load(ON_PREFERENCE_CLICK_LISTENER)
    developerSettings =
        Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
    preferenceFragmentCompat = Chrome.load(PREFERENCE_FRAGMENT_COMPAT)
    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
    appMenuPropertiesDelegateImpl = Chrome.load(APP_MENU_PROPERTIES_DELEGATE_IMPL)
    gURL = Chrome.load("org.chromium.url.GURL")
    readerModeManager = Chrome.load(READER_MODE_MANAGER)
    mClickListener = preference.getDeclaredField(CLICK_LISTENER_FIELD)
    // mOnClickListener = preference!!.getDeclaredField(PREFERENCE_CLICK_LISTENER_FIELD)
    mClickListener.setAccessible(true)
    mTab = readerModeManager.getDeclaredField(TAB_FIELD)
    mTab.setAccessible(true)
    mDistillerUrl = readerModeManager.getDeclaredField(DISTILLER_URL_FIELD)
    mDistillerUrl.setAccessible(true)
  }

  fun setClickListenerAndSummary(obj: Any, pref: String) {
    when (pref) {
      "exit" -> {
        mClickListener.set(obj, ExitDevMode)
      }
      "eruda" -> {
        mClickListener.set(obj, DownloadEruda)
        val version = getErudaVersion()
        var summary = "Click to install Eruda, size around 0.5 MiB"
        if (version != null) {
          summary = "Current version: " + version
        }
        obj.invokeMethod(summary) { name == SET_SUMMARY }
      }
    }
  }
}
