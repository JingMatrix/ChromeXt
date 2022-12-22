package org.matrix.chromext.hook

// import com.github.kyuubiran.ezxhelper.utils.Log
import android.content.Context
import android.os.Build
import android.view.Menu
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import java.lang.reflect.Method
import org.matrix.chromext.R
import org.matrix.chromext.proxy.MenuProxy

object MenuHook : BaseHook() {

  override fun init(ctx: Context) {

    val proxy = MenuProxy(ctx)

    if (!proxy.isDeveloper || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return
    }

    findMethod(proxy.chromeTabbedActivity!!) { name == proxy.MENU_KEYBOARD_ACTION }
        // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
        .hookAfter {
          val id = it.args[0] as Int
          if (ctx.getResources().getResourceName(id) ==
              "org.matrix.chromext:id/developer_tools_id") {
            UserScriptHook.proxy!!.openDevTools(ctx)
          }
        }

    findMethod(proxy.appMenuPropertiesDelegateImpl!!) { name == proxy.PREPARE_MENU }
        // public void prepareMenu(Menu menu, AppMenuHandler handler)
        .hookBefore {
          val menu = it.args[0] as Menu
          proxy.injectLocalMenu(it.thisObject, menu)
        }

    findMethod(proxy.preferenceFragmentCompat!!) { name == proxy.ADD_PREFERENCES_FROM_RESOURCE }
        // public void addPreferencesFromResource(Int preferencesResId)
        .hookBefore {
          if (it.thisObject::class.qualifiedName == proxy.developerSettings!!.getName()) {
            it.args[0] = R.xml.developer_preferences
          }
        }

    findMethod(proxy.preferenceFragmentCompat!!) { name == proxy.FIND_PREFERENCE }
        // public @Nullable T <T extends Preference> findPreference(@NonNull CharSequence key)
        .hookAfter {
          if (it.thisObject::class.qualifiedName == proxy.developerSettings!!.getName() &&
              (it.args[0] as String) == "beta_stable_hint") {

            val refThis = it
            arrayOf("eruda", "exit").forEach {
              proxy.setClickListener(
                  (refThis.method as Method).invoke(refThis.thisObject, it)!!, ctx, it)
            }
          }
        }
  }
}
