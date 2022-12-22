package org.matrix.chromext.hook

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import org.matrix.chromext.R
import org.matrix.chromext.proxy.MenuProxy

object MenuHook : BaseHook() {
  var meu_res_id: Int? = null
  override fun init(ctx: Context) {
    val menuProxy = MenuProxy(ctx)

    // findMethod(menuProxy.chromeTabbedActivity!!) { name == menuProxy.MENU_KEYBOARD_ACTION }
    //     // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
    //     .hookAfter {}

    findMethod(menuProxy.appMenuPropertiesDelegateImpl!!) { name == menuProxy.PREPARE_MENU }
        // public void prepareMenu(Menu menu, AppMenuHandler handler)
        .hookBefore {
          val menu = it.args[0] as Menu
          Log.i("Res: ${meu_res_id} replaces ${R.menu.main_menu}")
          val moduleContext =
              ctx.createPackageContext("org.matrix.chromext", Context.CONTEXT_IGNORE_SECURITY)
          // ctx.getResources().getResourceName(meu_res_id!!)
          val inflater = MenuInflater(moduleContext)
          Log.i("Local res: ${moduleContext.getResources().getResourceName(R.menu.main_menu)}")
          // inflater.inflate(R.menu.main_menu, menu)
          // menu.findItem(R.id.developer_tools_id).setVisible(true)
        }
  }
}
