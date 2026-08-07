package org.matrix.chromext.hook

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
    val proxy = PageInfoProxy
    val newRow = proxy.rowConstructor ?: throw NoSuchMethodException("PageInfoRowView(Context)")
    val title = proxy.mTitle ?: throw NoSuchFieldException("PageInfoRowView.mTitle")
    val show = proxy.showPageInfo ?: throw NoSuchMethodException("PageInfoController.show")
    val destroy = proxy.destroy ?: throw NoSuchMethodException("PageInfoController.destroy")
    if (proxy.nativeCallbacks.isEmpty()) throw NoSuchMethodException("PageInfoController callbacks")

    // The row container is looked up by resource id and never kept in a field, and Edge can swap
    // the whole PageInfoView for a bottom sheet behind a feature flag, so rather than chase it
    // through the controller we keep the first row built during show() and take its parent.
    var firstRow: ViewGroup? = null
    var controller: Any? = null
    var showing = false
    var inserted = false

    fun erudaRow(url: String, host: Any, parent: ViewGroup): View {
      val row = newRow.newInstance(parent.getContext(), null) as ViewGroup
      row.setVisibility(View.VISIBLE)
      (proxy.mIcon?.get(row) as? ImageView)?.setImageResource(R.drawable.ic_devtools)
      val subTitle = proxy.mSubtitle?.get(row) as? TextView
      (subTitle?.getParent() as? ViewGroup)?.removeView(subTitle)
      val label = title.get(row) as TextView
      val onClick: () -> Unit
      if (isChromeXtFrontEnd(url)) {
        label.setText(R.string.main_menu_developer_tools)
        onClick = { Listener.on("inspectPages") }
      } else if (isUserScript(url)) {
        label.setText(R.string.main_menu_install_script)
        onClick = {
          val sandBoxed = shouldBypassSandbox(url)
          Chrome.evaluateJavascript(listOf("Symbol.installScript(true);"), null, null, sandBoxed)
        }
      } else {
        label.setText(R.string.main_menu_eruda_console)
        onClick = { UserScriptProxy.evaluateJavascript(Local.openEruda) }
      }
      row.setOnClickListener {
        onClick()
        runCatching { destroy.invoke(host) }.onFailure { Log.ex(it) }
      }
      return row
    }

    fun insertRow(host: Any) {
      if (inserted) return
      val parent = firstRow?.getParent() as? ViewGroup ?: return
      val url = Chrome.getUrl() ?: return
      if (isChromeScheme(url)) return
      inserted = true
      parent.addView(erudaRow(url, host, parent))
    }

    show.hookBefore {
      showing = true
      inserted = false
      firstRow = null
      controller = null
    }

    newRow.hookAfter { if (showing && firstRow == null) firstRow = it.thisObject as ViewGroup }

    // Native runs these while show() is still on the stack, which is both where the controller
    // instance becomes reachable and the earliest point at which the rows are already laid out.
    proxy.nativeCallbacks.forEach { callback ->
      callback.hookAfter {
        if (!showing) return@hookAfter
        controller = it.thisObject
        insertRow(it.thisObject)
      }
    }

    show.hookAfter {
      showing = false
      controller?.let { host -> insertRow(host) }
      firstRow = null
      controller = null
    }

    isInit = true
  }
}
