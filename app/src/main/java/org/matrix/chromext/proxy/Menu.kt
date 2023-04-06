package org.matrix.chromext.proxy

import android.content.Context
import android.os.Build
import android.view.View
import android.view.View.OnClickListener
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.text.Regex
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod

const val ERUD_URL = "https://cdn.jsdelivr.net/npm/eruda@latest/eruda.min.js"

class MenuProxy() {

  // Grep chrome-distiller to get the class ReaderModeManager
  var READER_MODE_MANAGER = "js2"
  // the match is inside the method activateReaderMode

  // Grep Android.PrepareMenu.OpenWebApkVisibilityCheck to get the class
  // org/chromium/chrome/browser/app/appmenu/AppMenuPropertiesDelegateImpl.java
  var APP_MENU_PROPERTIES_DELEGATE_IMPL = "of"

  val chromeTabbedActivity: Class<*>
  val customTabActivity: Class<*>
  val appMenuPropertiesDelegateImpl: Class<*>
  val developerSettings: Class<*>
  val preferenceFragmentCompat: Class<*>
  val readerModeManager: Class<*>
  val twoStatePreference: Class<*>
  val gURL: Class<*>

  private val preference: Class<*>
  private val mClickListener: Field
  val mDistillerUrl: Field
  val mTab: Field

  val activateReadMode: Method
  val setChecked: Method
  val setSummary: Method

  val findPreference: Method

  val addPreferencesFromResource: Method

  var isDeveloper: Boolean = false

  init {
    if (Chrome.split && Chrome.version == 103) {
      READER_MODE_MANAGER = "BD2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Hg"
    }

    if (!Chrome.split && Chrome.version >= 108) {
      READER_MODE_MANAGER = "bH2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "lg"
    }

    if (!Chrome.split && Chrome.version >= 109) {
      READER_MODE_MANAGER = "KH2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "rg"
    }

    if (Chrome.split && Chrome.version >= 109) {
      READER_MODE_MANAGER = "Ss2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "tf"
    }

    if (!Chrome.split && Chrome.version >= 110) {
      READER_MODE_MANAGER = "lB2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Fg"
    }

    if (Chrome.split && Chrome.version >= 110) {
      READER_MODE_MANAGER = "pm2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Jf"
    }

    if (!Chrome.split && Chrome.version >= 111) {
      READER_MODE_MANAGER = "pD2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Kg"
    }

    if (Chrome.split && Chrome.version >= 111) {
      READER_MODE_MANAGER = "io2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Of"
    }

    val sharedPref =
        Chrome.getContext()
            .getSharedPreferences(
                Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
    isDeveloper = sharedPref!!.getBoolean("developer", false) || Chrome.isDev

    preference = Chrome.load("androidx.preference.Preference")
    developerSettings =
        Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
    preferenceFragmentCompat = developerSettings.getSuperclass() as Class<*>
    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
    customTabActivity = Chrome.load("org.chromium.chrome.browser.customtabs.CustomTabActivity")
    appMenuPropertiesDelegateImpl = Chrome.load(APP_MENU_PROPERTIES_DELEGATE_IMPL)
    twoStatePreference = Chrome.load("androidx/preference/g")
    gURL = Chrome.load("org.chromium.url.GURL")
    readerModeManager = Chrome.load(READER_MODE_MANAGER)
    mClickListener =
        preference.getDeclaredFields().find { it.getType() == OnClickListener::class.java }!!
    mClickListener.setAccessible(true)
    mTab =
        readerModeManager.getDeclaredFields().find {
          it.toString().startsWith("public final org.chromium.chrome.browser.tab.Tab")
        }!!
    mTab.setAccessible(true)
    mDistillerUrl = readerModeManager.getDeclaredFields().filter { it.type == gURL }.last()
    mDistillerUrl.setAccessible(true)
    activateReadMode =
        // This is purely luck, there are other methods with the same signatures
        findMethod(readerModeManager) { getParameterCount() == 0 && getReturnType() == Void.TYPE }
    setSummary =
        findMethod(preference) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == CharSequence::class.java &&
              getReturnType() == Void.TYPE
        }
    setChecked =
        findMethod(twoStatePreference) {
          getParameterCount() == 1 && getParameterTypes().first() == Boolean::class.java
        }
    findPreference =
        findMethod(preferenceFragmentCompat) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == CharSequence::class.java &&
              getReturnType() == preference
        }
    addPreferencesFromResource =
        findMethod(preferenceFragmentCompat) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == Int::class.java &&
              getReturnType() == Void.TYPE
        }
  }

  fun setClickListener(obj: Any, pref: String) {
    val ctx = Chrome.getContext()
    val sharedPref =
        ctx.getSharedPreferences(
            Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
    when (pref) {
      "exit" -> {
        mClickListener.set(
            obj,
            object : OnClickListener {
              override fun onClick(v: View) {
                if (Chrome.isDev) {
                  Log.toast(ctx, "This function is not available for your Chrome build")
                  return
                }
                with(sharedPref.edit()) {
                  putBoolean("developer", false)
                  apply()
                  Log.toast(ctx, "Please restart Chrome to apply the changes")
                }
              }
            })
      }
      "eruda" -> {
        val version = sharedPref.getString("eruda_version", "unknown")
        var summary = "Click to install Eruda, size around 0.5 MiB"
        if (version != "unknown") {
          summary = "Current version: v" + version
        }
        setSummary.invoke(obj, summary)
        mClickListener.set(
            obj,
            object : OnClickListener {
              override fun onClick(v: View) {
                Download.start(ERUD_URL, "Download/Eruda.js", true) {
                  val old_version = sharedPref.getString("eruda_version", "unknown")
                  var new_version = old_version
                  val verisonReg = Regex("""/npm/eruda@(?<version>[\d\.]+)/eruda""")
                  val vMatchGroup =
                      verisonReg.find(it.take(150))?.groups as? MatchNamedGroupCollection
                  if (vMatchGroup != null) {
                    new_version = vMatchGroup.get("version")?.value as String
                  }
                  if (old_version != new_version) {
                    with(sharedPref.edit()) {
                      putString("eruda_version", new_version)
                      apply()
                    }
                    Log.toast(ctx, "Updated to eruda v" + new_version)
                    setSummary.invoke(obj, "Current version: v" + new_version)
                  } else {
                    Log.toast(ctx, "Eruda is already the lastest")
                  }
                }
              }
            })
      }
      "gesture_mod" -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          setChecked.invoke(obj, sharedPref.getBoolean("gesture_mod", true))
          mClickListener.set(
              obj,
              object : OnClickListener {
                override fun onClick(v: View) {
                  with(sharedPref.edit()) {
                    putBoolean("gesture_mod", !sharedPref.getBoolean("gesture_mod", true))
                    apply()
                  }
                  setChecked.invoke(obj, sharedPref.getBoolean("gesture_mod", true))
                }
              })
        } else {
          setChecked.invoke(obj, false)
          mClickListener.set(
              obj,
              object : OnClickListener {
                override fun onClick(_v: View) {
                  setChecked.invoke(obj, false)
                  Log.toast(ctx, "Only avaible after Android 10")
                }
              })
        }
      }
    }
  }
}
