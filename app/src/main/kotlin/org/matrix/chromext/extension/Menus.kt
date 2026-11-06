package org.matrix.chromext.extension

import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.utils.Log

/**
 * Extension entries in ChromeXt's page menu.
 *
 * `contextMenus.create` items are kept in memory by [ExtensionRuntime]; this turns them into rows
 * of the browser menu ChromeXt already injects into, and reports the click back to the extension.
 * Chrome shows no toolbar icon for an unpacked extension, so `action` gets a row of its own: it
 * opens `default_popup` when there is one and fires `onClicked` otherwise.
 */
object Menus {

  private const val BASE_ID = 0x71000000

  class Entry(
      val id: Int,
      val title: String,
      val extension: String,
      val kind: String,
      val itemId: String,
  )

  private val built = mutableMapOf<Int, Entry>()

  /** Menu rows for the given page, stable until the next menu is opened. */
  fun entries(url: String): List<Entry> {
    val rows = mutableListOf<Entry>()
    var index = 0
    Extensions.enabled().forEach { extension ->
      ExtensionRuntime.menuItems(extension.id).forEach { item ->
        if (!visible(item, url)) return@forEach
        if (!item.optBoolean("enabled", true)) return@forEach
        val title = item.optString("title").ifEmpty { extension.name }
        rows.add(Entry(BASE_ID + index++, title, extension.id, "contextMenu", item.optString("id")))
      }
      if (hasAction(extension)) {
        rows.add(Entry(BASE_ID + index++, extension.name, extension.id, "action", ""))
      }
    }
    built.clear()
    rows.forEach { built[it.id] = it }
    if (rows.isNotEmpty())
        Log.d("Menus: " + rows.joinToString { "${it.kind}" + "='" + it.title + "'" })
    return rows
  }

  private fun hasAction(extension: Extension): Boolean =
      extension.manifest.has("action") ||
          extension.manifest.has("browser_action") ||
          extension.manifest.has("page_action")

  private fun visible(item: JSONObject, url: String): Boolean {
    val contexts =
        item.optJSONArray("contexts")?.let { array ->
          (0 until array.length()).map { array.optString(it) }
        } ?: listOf("page")
    // Only the page context exists here: there is no selection, link or image information
    if (contexts.none { it == "all" || it == "page" }) return false
    item.optJSONArray("documentUrlPatterns")?.let { patterns ->
      val list = (0 until patterns.length()).map { patterns.optString(it) }
      if (list.isNotEmpty() && list.none { MatchPattern.matches(it, url) }) return false
    }
    return true
  }

  fun rowFor(id: Int): Entry? = built[id]

  /** Report a click back to the extension that owns the row. */
  fun dispatch(id: Int): Boolean {
    val row = built[id] ?: return false
    val extension = Extensions.byId(row.extension) ?: return false
    val url = Chrome.getUrl() ?: ""
    val tab = tabInfo()
    when (row.kind) {
      "contextMenu" -> {
        Log.i("${row.extension}: context menu ${row.itemId} clicked")
        Background.evaluate(
            "__chromextHostContextMenu(${JSONObject.quote(row.extension)}," +
                " ${JSONObject().put("menuItemId", row.itemId).put("pageUrl", url).put("frameId", 0)}," +
                " $tab);")
      }
      "action" -> {
        val popup = ExtensionRuntime.popupOf(extension)
        if (popup.isNotEmpty()) {
          val target = ExtServer.origin(extension) + "/" + popup.removePrefix("/")
          Log.i("${row.extension}: opening its popup $target")
          ExtensionRuntime.openInNewTab(target)
        } else {
          Log.i("${row.extension}: action clicked")
          Background.evaluate("__chromextHostAction(${JSONObject.quote(row.extension)}, $tab);")
        }
      }
    }
    return true
  }

  private fun tabInfo(): JSONObject {
    val tabId = Chrome.getTab()?.let { UserScriptProxy.getTabId(it).toIntOrNull() } ?: 0
    val tab =
        JSONObject()
            .put("id", tabId)
            .put("url", Chrome.getUrl() ?: "")
            .put("active", true)
            .put("windowId", 1)
            .put("index", 0)
    return JSONObject().put("tab", tab).put("frameId", 0)
  }
}
