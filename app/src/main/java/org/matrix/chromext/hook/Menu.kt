package org.matrix.chromext.hook

import android.app.Activity
import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import java.lang.reflect.Method
import java.util.ArrayList
import org.matrix.chromext.GestureConflict
import org.matrix.chromext.R
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object MenuHook : BaseHook() {

  override fun init(ctx: Context, split: Boolean) {

    val proxy = MenuProxy(ctx, split)

    // Page menu only appear after restarting chrome
    if (proxy.isDeveloper) {
      findMethod(proxy.chromeTabbedActivity) { name == proxy.MENU_KEYBOARD_ACTION }
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
          .hookAfter {
            val id = it.args[0] as Int
            val name = ctx.getResources().getResourceName(id)
            if (name == "org.matrix.chromext:id/developer_tools_id") {
              UserScriptHook.proxy!!.openDevTools(ctx)
            }
          }

      findMethod(proxy.appMenuPropertiesDelegateImpl) {
            name == proxy.UPDATE_REQUEST_DESKTOP_SITE_MENU_ITEM
          }
          // protected void updateRequestDesktopSiteMenuItem(Menu menu, @Nullable Tab currentTab,
          //         boolean canShowRequestDesktopSite, boolean isChromeScheme)
          .hookBefore {
            val menu = it.args[0] as Menu
            MenuInflater(ctx).inflate(R.menu.main_menu, menu)

            val mItems = menu::class.java.getDeclaredField("mItems")
            mItems.setAccessible(true)

            @Suppress("UNCHECKED_CAST") val items = mItems.get(menu) as ArrayList<MenuItem>

            val devMenuItem: MenuItem = items.removeLast()
            devMenuItem.setVisible(!(it.args[3] as Boolean) && (it.args[2] as Boolean))
            // The index 13 is just chosen by tests, to make sure that it appears before the share
            // menu
            items.add(13, devMenuItem)
            mItems.setAccessible(false)
          }
    }

    // Preference menu doesn't work with non-split version
    if (split) {
      findMethod(proxy.chromeTabbedActivity) { name == "onStart" }
          .hookAfter {
            val activity = it.thisObject as Activity
            GestureConflict.hookActivity(activity)
          }

      findMethod(proxy.preferenceFragmentCompat) { name == proxy.ADD_PREFERENCES_FROM_RESOURCE }
          // public void addPreferencesFromResource(Int preferencesResId)
          .hookBefore {
            if (it.thisObject::class.qualifiedName == proxy.developerSettings.name) {
              it.args[0] = R.xml.developer_preferences
            }
          }

      findMethod(proxy.preferenceFragmentCompat) { name == proxy.FIND_PREFERENCE }
          // public @Nullable T <T extends Preference> findPreference(@NonNull CharSequence key)
          .hookAfter {
            if (it.thisObject::class.qualifiedName == proxy.developerSettings.name &&
                (it.args[0] as String) == "beta_stable_hint") {

              val refThis = it
              arrayOf("eruda", "exit").forEach {
                proxy.setClickListenerAndSummary(
                    (refThis.method as Method).invoke(refThis.thisObject, it)!!, ctx, it)
              }
            }
          }
    }
  }
}
