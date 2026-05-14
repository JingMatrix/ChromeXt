package org.matrix.chromext.hook

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.proxy.PageInfoProxy
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.*

object PageInfoHook : BaseHook() {

  override fun init() {

    if (ContextMenuHook.isInit) return
    var controller: Any? = null
    val proxy = PageInfoProxy

    fun addErudaRow(url: String): ViewGroup? {
      return try {
        val constructor = proxy.pageInfoRowView.declaredConstructors.firstOrNull {
          it.parameterTypes.size == 2 &&
          it.parameterTypes[0] == android.content.Context::class.java
        } ?: proxy.pageInfoRowView.declaredConstructors[0]
        
        val infoRow =
            constructor.newInstance(Chrome.getContext(), null)
                as ViewGroup
        infoRow.setVisibility(View.VISIBLE)
        
        val icon = proxy.mIcon.get(infoRow) as? ImageView
        icon?.setImageResource(R.drawable.ic_devtools)
        
        val subTitle = proxy.mSubtitle.get(infoRow) as? TextView
        (subTitle?.getParent() as? ViewGroup)?.removeView(subTitle)
        
        val title = proxy.mTitle.get(infoRow) as? TextView ?: return null
        if (isChromeXtFrontEnd(url)) {
          title.setText(R.string.main_menu_developer_tools)
          infoRow.setOnClickListener {
            Listener.on("inspectPages")
            controller?.invokeMethod { name == "destroy" }
          }
        } else if (isUserScript(url)) {
          title.setText(R.string.main_menu_install_script)
          infoRow.setOnClickListener {
            val sandBoxed = shouldBypassSandbox(url)
            Chrome.evaluateJavascript(listOf("Symbol.installScript(true);"), null, null, sandBoxed)
            controller?.invokeMethod { name == "destroy" }
          }
        } else {
          title.setText(R.string.main_menu_eruda_console)
          infoRow.setOnClickListener {
            UserScriptProxy.evaluateJavascript(Local.openEruda)
            controller?.invokeMethod { name == "destroy" }
          }
        }
        infoRow
      } catch (e: Exception) {
        Log.ex(e)
        null
      }
    }

    try {
      proxy.pageInfoControllerRef.declaredConstructors[0].hookAfter { controller = it.thisObject }
    } catch (e: Exception) {
      Log.ex(e)
    }

    try {
      proxy.pageInfoController.declaredConstructors[0].hookAfter {
        val url = Chrome.getUrl() ?: return@hookAfter
        if (isChromeScheme(url) || controller == null) return@hookAfter
        try {
          val view = proxy.mView.get(it.thisObject)
          val rowWrapper = proxy.mRowWrapper.get(view) as? LinearLayout
          val erudaRow = addErudaRow(url)
          if (rowWrapper != null && erudaRow != null) {
            rowWrapper.addView(erudaRow)
          }
        } catch (e: Exception) {
          Log.ex(e)
        }
      }
    } catch (e: Exception) {
      Log.ex(e)
    }

    // readerMode.init(Chrome.load("org.chromium.chrome.browser.dom_distiller.ReaderModeManager"))
    isInit = true
  }
}
