package org.matrix.chromext.hook

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayList
import org.matrix.chromext.Chrome
import org.matrix.chromext.DevTools
import org.matrix.chromext.R
import org.matrix.chromext.ResourceMerge
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.proxy.TabModel
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.hookMethod

object MenuHook : BaseHook() {

  override fun init() {

    val proxy = MenuProxy()

    // Page menu only appears after restarting chrome

    val ctx = Chrome.getContext()
    var readerModeManager: Any? = null
    var mDistillerUrl: Field? = null
    var mTab: Field? = null
    var activateReadMode: Method? = null
    val READER_MODE_ID = 31415926
    val erudaView =
        View.inflate(Chrome.getContext(), R.layout.page_info, null)
            .findViewById(R.id.page_info_eruda_row) as TextView

    if (Chrome.isEdge) {
      val pageInfoView = Chrome.load("org.chromium.components.page_info.PageInfoView")
      val mRowWrapper =
          pageInfoView.getDeclaredFields().find { it.type == LinearLayout::class.java }
      pageInfoView.getDeclaredConstructors()[0].hookAfter {
        (erudaView.getParent() as LinearLayout).removeView(erudaView)
        (mRowWrapper!!.get(it.thisObject) as LinearLayout).addView(erudaView)
      }

      Chrome.load("org.chromium.chrome.browser.dom_distiller.ReaderModeManager")
          .getDeclaredConstructors()[0]
          .hookAfter { readerModeManager = it.thisObject }
    }

    var findReaderHook: Unhook? = null
    findReaderHook =
        findMethod(proxy.emptyTabObserver) { getParameterCount() == 6 }
            // A method has the most code must be called for the initialization
            .hookAfter {
              val subType = it.thisObject::class.java
              if (subType.getInterfaces().size == 1 &&
                  subType.getDeclaredFields().find {
                    it.toString().startsWith("public org.chromium.ui.modelutil.PropertyModel")
                  } != null) {
                readerModeManager = it.thisObject
                findReaderHook!!.unhook()
                mTab =
                    subType.getDeclaredFields().find {
                      it.toString().startsWith("public final org.chromium.chrome.browser.tab.Tab")
                    }!!
                mTab!!.setAccessible(true)
                mDistillerUrl = subType.getDeclaredFields().filter { it.type == proxy.gURL }.last()
                mDistillerUrl!!.setAccessible(true)
                activateReadMode =
                    // This is purely luck, there are other methods with the same signatures
                    findMethod(subType) { getParameterCount() == 0 && getReturnType() == Void.TYPE }
              }
            }

    fun menuHandler(id: Int): Boolean {
      if (id == READER_MODE_ID) {
        activateReadMode!!.invoke(readerModeManager!!)
        return true
      }
      when (ctx.getResources().getResourceName(id)) {
        "org.matrix.chromext:id/install_script_id" -> {
          Download.start(TabModel.getUrl(), "UserScript/script.js") {
            ScriptDbManager.on("installScript", it)
          }
        }
        "org.matrix.chromext:id/developer_tools_id" -> DevTools.start()
        "org.matrix.chromext:id/eruda_console_id" ->
            UserScriptHook.proxy!!.evaluateJavaScript(TabModel.openEruda())
      }
      return false
    }

    findMethod(proxy.chromeTabbedActivity) {
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
          getParameterCount() == 2 &&
              getParameterTypes().first() == Int::class.java &&
              getParameterTypes().last() == Boolean::class.java &&
              getReturnType() == Boolean::class.java
        }
        .hookBefore {
          if (menuHandler(it.args[0] as Int)) {
            it.result = true
          }
        }

    var findMenuHook: Unhook? = null
    findMenuHook =
        findMethod(proxy.chromeTabbedActivity) {
              getParameterCount() == 0 &&
                  getReturnType().isInterface() &&
                  getReturnType().getDeclaredMethods().size > 6
            }
            .hookAfter {
              val appMenuPropertiesDelegateImpl = it.result::class.java.getSuperclass() as Class<*>
              findMenuHook!!.unhook()
              findMethod(appMenuPropertiesDelegateImpl, true) {
                    getParameterCount() == 4 &&
                        getParameterTypes().first() == Menu::class.java &&
                        getParameterTypes().last() == Boolean::class.java &&
                        getReturnType() == Void.TYPE
                  }
                  // protected void updateRequestDesktopSiteMenuItem(Menu menu, @Nullable Tab
                  // currentTab, boolean canShowRequestDesktopSite, boolean isChromeScheme)
                  .hookBefore {
                    val menu = it.args[0] as Menu

                    if (menu.size() <= 20 || TabModel.getUrl().startsWith("chrome")) {
                      // Infalte only for the main_menu, which has more than 20 items at least
                      return@hookBefore
                    }

                    if (menu.getItem(0).hasSubMenu() && readerModeManager != null) {
                      // The first menu item shou be the row_menu
                      // Brave browser not supported for unknown reason
                      val infoMenu = menu.getItem(0).getSubMenu()!!.getItem(3)
                      infoMenu.setIcon(R.drawable.ic_book)
                      infoMenu.setEnabled(true)
                      val mId = infoMenu::class.java.getDeclaredField("mId")
                      mId.setAccessible(true)
                      mId.set(infoMenu, READER_MODE_ID)
                      mId.setAccessible(false)
                      mTab!!.set(readerModeManager!!, it.args[1])
                      mDistillerUrl!!.set(
                          readerModeManager!!,
                          proxy.gURL
                              .getDeclaredConstructors()[1]
                              .newInstance("https://github.com/JingMatrix/ChromeXt"))
                      // We need a mock url to finish the cleanup logic readerModeManager
                    }

                    MenuInflater(ctx).inflate(R.menu.main_menu, menu)

                    val mItems = menu::class.java.getDeclaredField("mItems")
                    mItems.setAccessible(true)

                    @Suppress("UNCHECKED_CAST") val items = mItems.get(menu) as ArrayList<MenuItem>

                    if (TabModel.getUrl().endsWith("/ChromeXt/") && proxy.isDeveloper) {
                      // Drop the Eruda console menu
                      items.removeLast()
                    }

                    if (TabModel.getUrl().endsWith(".user.js")) {
                      // Drop the Eruda console and the Dev Tools menus
                      items.removeLast()
                      items.removeLast()
                    }

                    val magicMenuItem: MenuItem = items.removeLast()
                    magicMenuItem.setVisible(!(it.args[3] as Boolean) && (it.args[2] as Boolean))
                    // The index 14 is just chosen to make sure that it
                    // appears before the share menu
                    items.add(14, magicMenuItem)
                    mItems.setAccessible(false)
                  }
            }

    proxy.addPreferencesFromResource
        // public void addPreferencesFromResource(Int preferencesResId)
        .hookMethod {
          before {
            if (it.thisObject::class.qualifiedName == proxy.developerSettings.name) {
              it.args[0] = R.xml.developer_preferences
            }
          }

          after {
            if (it.thisObject::class.qualifiedName == proxy.developerSettings.name) {
              val refThis = it
              val preferences = mutableMapOf<String, Any>()
              arrayOf("eruda", "exit", "gesture_mod").forEach {
                preferences[it] = proxy.findPreference.invoke(refThis.thisObject, it)!!
              }
              proxy.setClickListener(preferences.toMap())
            }
          }
        }

    // if (Chrome.isVivaldi) {
    //   findMethod(proxy.mainSettings) {
    //         getParameterCount() == 1 &&
    //             getParameterTypes().first() == String::class.java &&
    //             getReturnType() == proxy.preference
    //       }
    //       // Preference addPreferenceIfAbsent(String key)
    //       .hookBefore {
    //         // if ((it.args[0] as String) == "homepage") {
    //         //   (it.method as Method).invoke(it.thisObject, "developer")
    //         // }
    //       }
    // }

    // Usually, only some versions of Chrome or Android < 11
    // need the following context-enriching code.
    // It is better to always run them, just in case.
    var preferenceEnrichHook: Unhook? = null
    var menuEnrichHook: Unhook? = null
    preferenceEnrichHook =
        findMethod(proxy.preferenceFragmentCompat, true) {
              getParameterCount() == 0 && getReturnType() == Context::class.java
              // Purely luck to get a method returning the context
            }
            .hookAfter {
              if (it.thisObject::class.qualifiedName == proxy.developerSettings.name) {
                ResourceMerge.enrich(it.getResult() as Context)
                preferenceEnrichHook!!.unhook()
              }
            }

    menuEnrichHook =
        proxy.windowAndroid.getDeclaredConstructors()[1].hookAfter {
          val context = it.args[0] as Context
          ResourceMerge.enrich(context)
          menuEnrichHook!!.unhook()
        }
  }
}
