package org.matrix.chromext.hook

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.util.ArrayList
import org.matrix.chromext.Chrome
import org.matrix.chromext.DevTools
import org.matrix.chromext.R
import org.matrix.chromext.ResourceMerge
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.proxy.TabModel
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.hookMethod
import org.matrix.chromext.utils.invokeMethod

object readerMode {
  val ID = 31415926
  private var readerModeManager: Class<*>? = null

  fun isInit(): Boolean {
    return readerModeManager != null
  }

  fun init(managerClass: Class<*>) {
    readerModeManager = managerClass
  }

  fun enable() {
    val mDistillerUrl =
        readerModeManager!!.getDeclaredFields().filter { it.type == UserScriptProxy.gURL }.last()!!
    val activateReadMode =
        // This is purely luck, there are other methods with the same signatures
        findMethod(readerModeManager!!) { getParameterCount() == 0 && getReturnType() == Void.TYPE }

    val manager =
        readerModeManager!!.getDeclaredConstructors()[0].newInstance(TabModel.getTab(), null)

    mDistillerUrl.setAccessible(true)
    mDistillerUrl.set(
        manager,
        UserScriptProxy.gURL
            .getDeclaredConstructors()[1]
            .newInstance("https://github.com/JingMatrix/ChromeXt"))
    mDistillerUrl.setAccessible(false)

    activateReadMode.invoke(manager)
  }
}

object MenuHook : BaseHook() {

  override fun init() {

    val proxy = MenuProxy

    if (Chrome.isEdge) {
      // Add eruda menu to page_info dialog
      var mWebContentsObserver: Any? = null
      proxy.webContentsObserver.getDeclaredConstructors()[0].hookAfter {
        mWebContentsObserver = it.thisObject
      }
      proxy.pageInfoView.getDeclaredConstructors()[0].hookAfter {
        val url = TabModel.getUrl()
        if (!url.startsWith("edge://")) {
          val erudaRow =
              proxy.pageInfoRowView
                  .getDeclaredConstructors()[0]
                  .newInstance(Chrome.getContext(), null) as ViewGroup
          erudaRow.setVisibility(View.VISIBLE)
          val icon = proxy.mIcon.get(erudaRow) as ImageView
          icon.setImageResource(R.drawable.ic_devtools)
          val subTitle = proxy.mSubtitle.get(erudaRow) as TextView
          (subTitle.getParent() as? ViewGroup)?.removeView(subTitle)
          val title = proxy.mTitle.get(erudaRow) as TextView
          if (url.endsWith("/ChromeXt/")) {
            title.setText("Open developer tools")
            erudaRow.setOnClickListener {
              DevTools.start()
              mWebContentsObserver!!.invokeMethod() { name == "destroy" }
            }
          } else {
            title.setText("Open eruda console")
            erudaRow.setOnClickListener {
              UserScriptProxy.evaluateJavaScript(TabModel.openEruda())
              mWebContentsObserver!!.invokeMethod() { name == "destroy" }
            }
          }
          (proxy.mRowWrapper.get(it.thisObject) as LinearLayout).addView(erudaRow)
        }
      }

      // readerMode.init(Chrome.load("org.chromium.chrome.browser.dom_distiller.ReaderModeManager"))
    } else {

      fun menuHandler(id: Int): Boolean {
        if (id == readerMode.ID) {
          readerMode.enable()
          return true
        }
        when (Chrome.getContext().resources.getResourceName(id)) {
          "org.matrix.chromext:id/install_script_id" -> {
            Download.start(TabModel.getUrl(), "UserScript/script.js") {
              ScriptDbManager.on("installScript", it)
            }
          }
          "org.matrix.chromext:id/developer_tools_id" -> DevTools.start()
          "org.matrix.chromext:id/eruda_console_id" ->
              UserScriptProxy.evaluateJavaScript(TabModel.openEruda())
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
                val appMenuPropertiesDelegateImpl =
                    it.result::class.java.getSuperclass() as Class<*>
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

                      if (menu.getItem(0).hasSubMenu() && readerMode.isInit()) {
                        // The first menu item shou be the row_menu
                        // Brave browser not supported for unknown reason
                        val infoMenu = menu.getItem(0).getSubMenu()!!.getItem(3)
                        infoMenu.setIcon(R.drawable.ic_book)
                        infoMenu.setEnabled(true)
                        val mId = infoMenu::class.java.getDeclaredField("mId")
                        mId.setAccessible(true)
                        mId.set(infoMenu, readerMode.ID)
                        mId.setAccessible(false)
                      }

                      MenuInflater(Chrome.getContext()).inflate(R.menu.main_menu, menu)

                      val mItems = menu::class.java.getDeclaredField("mItems")
                      mItems.setAccessible(true)

                      @Suppress("UNCHECKED_CAST")
                      val items = mItems.get(menu) as ArrayList<MenuItem>

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

      var findReaderHook: Unhook? = null
      findReaderHook =
          proxy.emptyTabObserver.getDeclaredConstructors()[0].hookAfter {
            val subType = it.thisObject::class.java
            if (subType.getInterfaces().size == 1 &&
                subType.getDeclaredFields().find {
                  it.toString().startsWith("public org.chromium.ui.modelutil.PropertyModel")
                } != null) {
              readerMode.init(subType)
              findReaderHook!!.unhook()
            }
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

    findMethod(proxy.preferenceFragmentCompat, true) {
          getParameterCount() == 0 && getReturnType() == Context::class.java
          // Purely luck to get a method returning the context
        }
        .hookAfter {
          if (it.thisObject::class.qualifiedName == proxy.developerSettings.name) {
            ResourceMerge.enrich(it.getResult() as Context)
          }
        }
  }
}
