package org.matrix.chromext.hook

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.util.ArrayList
import org.matrix.chromext.Chrome
import org.matrix.chromext.DevTools
import org.matrix.chromext.R
import org.matrix.chromext.ResourceMerge
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.proxy.TabModel
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.hookMethod
import org.matrix.chromext.utils.invokeMethod

object MenuHook : BaseHook() {

  override fun init() {

    val proxy = MenuProxy()
    var enrichHook: Unhook? = null

    // Page menu only appears after restarting chrome

    val ctx = Chrome.getContext()
    ResourceMerge.enrich(ctx)
    var readerModeManager: Any? = null

    proxy.readerModeManager.getDeclaredConstructors()[0].hookAfter {
      readerModeManager = it.thisObject
    }

    fun menuHandler(id: Int): Boolean {
      val name = ctx.getResources().getResourceName(id)
      when (name) {
        "org.matrix.chromext:id/install_script_id" -> {
          Download.start(TabModel.getUrl(), "UserScript/script.js") {
            UserScriptHook.proxy!!.scriptManager.on("installScript", it)
          }
        }
        "org.matrix.chromext:id/developer_tools_id" -> DevTools.start()
        "org.matrix.chromext:id/eruda_console_id" ->
            UserScriptHook.proxy!!.evaluateJavaScript(TabModel.openEruda())
        "com.android.chrome:id/info_menu_id" -> {
          if (proxy.mDistillerUrl.get(readerModeManager!!) != null) {
            // No idea why I must use getName() instead of name
            readerModeManager!!.invokeMethod() { getName() == proxy.ACTIVATE_READER_MODE }
            return true
          }
        }
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

    findMethod(proxy.appMenuPropertiesDelegateImpl) {
          getParameterCount() == 4 &&
              getParameterTypes().first() == Menu::class.java &&
              getParameterTypes().last() == Boolean::class.java &&
              getReturnType() == Void.TYPE
        }
        // protected void updateRequestDesktopSiteMenuItem(Menu menu, @Nullable Tab currentTab,
        //         boolean canShowRequestDesktopSite, boolean isChromeScheme)
        .hookBefore {
          val menu = it.args[0] as Menu

          if (menu.size() <= 20 || TabModel.getUrl().startsWith("chrome")) {
            // Infalte only for the main_menu, which has more than 20 items at least
            return@hookBefore
          }

          if (menu.getItem(0).hasSubMenu() &&
              !(it.args[3] as Boolean) &&
              readerModeManager != null) {
            // The first menu item shou be the row_menu
            val infoMenu = menu.getItem(0).getSubMenu()!!.getItem(3)
            infoMenu.setIcon(R.drawable.ic_book)
            infoMenu.setEnabled(true)
            proxy.mTab.set(readerModeManager!!, it.args[1])
            proxy.mDistillerUrl.set(
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
          // The index 13 is just chosen by tests, to make sure that it appears before the share
          // menu
          items.add(13, magicMenuItem)
          mItems.setAccessible(false)
        }

    if (!Chrome.split || Chrome.version == 109) {
      // No idea when we need to enrich
      enrichHook =
          findMethod(proxy.preferenceFragmentCompat.getSuperclass() as Class<*>) {
                getParameterCount() == 0 && getReturnType() == Context::class.java
              }
              .hookAfter {
                if (it.thisObject::class.qualifiedName == proxy.developerSettings.name) {
                  ResourceMerge.enrich(it.getResult() as Context)
                  if (enrichHook != null) {
                    enrichHook!!.unhook()
                  }
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
              arrayOf("eruda", "exit", "gesture_mod").forEach {
                proxy.setClickListener(proxy.findPreference.invoke(refThis.thisObject, it)!!, it)
              }
            }
          }
        }
  }
}
