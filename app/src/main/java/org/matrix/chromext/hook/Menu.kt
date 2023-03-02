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
        "org.matrix.chromext:id/developer_tools_id" -> DevTools.start()
        "org.matrix.chromext:id/eruda_console_id" ->
            UserScriptHook.proxy!!.evaluateJavaScript(TabModel.openEruda())
        "com.android.chrome:id/info_menu_id" -> {
          // No idea why I must use getName() instead of name
          readerModeManager!!.invokeMethod() { getName() == proxy.ACTIVATE_READER_MODE }
          return true
        }
      }
      return false
    }

    findMethod(proxy.chromeTabbedActivity) { name == proxy.MENU_KEYBOARD_ACTION }
        // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
        .hookBefore {
          if (menuHandler(it.args[0] as Int)) {
            it.result = true
          }
        }

    findMethod(proxy.customTabActivity) { name == proxy.MENU_KEYBOARD_ACTION }
        // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
        .hookBefore {
          if (menuHandler(it.args[0] as Int)) {
            it.result = true
          }
        }

    findMethod(proxy.appMenuPropertiesDelegateImpl) {
          name == proxy.UPDATE_REQUEST_DESKTOP_SITE_MENU_ITEM
        }
        // protected void updateRequestDesktopSiteMenuItem(Menu menu, @Nullable Tab currentTab,
        //         boolean canShowRequestDesktopSite, boolean isChromeScheme)
        .hookBefore {
          val menu = it.args[0] as Menu

          if (menu.getItem(0).hasSubMenu()) {
            // The first menu item shou be the row_menu
            val infoMenu = menu.getItem(0).getSubMenu()!!.getItem(3)
            infoMenu.setIcon(R.drawable.ic_book)
            infoMenu.setEnabled(!(it.args[3] as Boolean) && readerModeManager != null)
            proxy.mTab.set(readerModeManager!!, it.args[1])
            proxy.mDistillerUrl.set(
                readerModeManager!!,
                // We need a mock url to finish the cleanup logic readerModeManager
                proxy.gURL
                    .getDeclaredConstructors()[1]
                    .newInstance("https://github.com/JingMatrix/ChromeXt"))
          }

          if (menu.size() <= 20 || TabModel.getUrl().startsWith("chrome")) {
            // Infalte only for the main_menu, which has more than 20 items at least
            return@hookBefore
          }

          MenuInflater(ctx).inflate(R.menu.main_menu, menu)

          val mItems = menu::class.java.getDeclaredField("mItems")
          mItems.setAccessible(true)

          @Suppress("UNCHECKED_CAST") val items = mItems.get(menu) as ArrayList<MenuItem>

          if (TabModel.getUrl().endsWith("/ChromeXt/") && proxy.isDeveloper) {
            // Drop the Eruda console menu
            items.removeLast()
          }
          val devMenuItem: MenuItem = items.removeLast()
          devMenuItem.setVisible(!(it.args[3] as Boolean) && (it.args[2] as Boolean))
          // The index 13 is just chosen by tests, to make sure that it appears before the share
          // menu
          items.add(13, devMenuItem)
          mItems.setAccessible(false)
        }

    if (!Chrome.split || Chrome.version == 109) {
      // No idea when we need to enrich
      enrichHook =
          findMethod(proxy.preferenceFragmentCompat, true) { name == proxy.GET_CONTEXT }
              .hookAfter {
                if (it.thisObject::class.qualifiedName == proxy.developerSettings.name) {
                  ResourceMerge.enrich(it.getResult() as Context)
                  if (enrichHook != null) {
                    enrichHook!!.unhook()
                  }
                }
              }
    }

    findMethod(proxy.preferenceFragmentCompat) { name == proxy.ADD_PREFERENCES_FROM_RESOURCE }
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
