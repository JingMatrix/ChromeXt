package org.matrix.chromext.extension

import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.utils.Log

/**
 * The bridge between the JavaScript shim (content scripts and background pages) and Chrome.
 *
 * Content scripts reach it through the ChromeXt action channel. Background pages sit in an iframe
 * of the host page, which relays their postMessage traffic to the same channel.
 */
object ExtensionRuntime {

  class Source(
      val extension: String,
      val host: Boolean,
      val tab: Any? = null,
      val frameId: String? = null,
  )

  private val pending = mutableMapOf<String, (Boolean, Any?) -> Unit>()
  private val ports = mutableMapOf<String, Source>()
  private val contextMenus = mutableMapOf<String, MutableList<JSONObject>>()
  private val badges = mutableMapOf<String, String>()

  // ---- entry points ----------------------------------------------------------

  /** Called from Listener for every action dispatched by a page. */
  fun dispatch(payload: String, tab: Any?, frameId: String?): String? {
    val data = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    val host = data.optBoolean("host", false)
    if (data.optString("action") == "host.ready") {
      Background.onHostReady()
      return null
    }
    val extension = data.optString("ext")
    val source = Source(extension, host, tab, frameId)
    if (Extensions.byId(extension) == null) {
      Log.d("Dropping action for unknown extension ${extension}")
      return null
    }
    runCatching { handle(data, source) }
        .onFailure {
          Log.ex(it, "Extension action failure")
          replyError(data, source, it)
        }
    return null
  }

  // ---- plumbing --------------------------------------------------------------

  private fun replyError(payload: JSONObject, source: Source, error: Throwable) {
    send(source, payload.optString("uuid"), false, error.message ?: error::class.java.name)
  }

  private fun jsonOf(value: Any?): String =
      when (value) {
        null -> ""
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        is Number,
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
      }

  private fun send(source: Source, uuid: String, ok: Boolean, value: Any?) {
    if (uuid.isEmpty()) return
    val json = jsonOf(value)
    if (source.host) {
      Background.evaluate(
          "__chromextHostResolve(${JSONObject.quote(source.extension)}, " +
              "${JSONObject.quote(uuid)}, $ok, ${JSONObject.quote(json)});")
      return
    }
    val code = "__chromextResolve(${JSONObject.quote(uuid)}, $ok, ${JSONObject.quote(json)});"
    val tab = source.tab
    if (tab == null) Chrome.evaluateJavascript(listOf(code), null, source.frameId)
    else deliver(tab, code)
  }

  private fun handle(payload: JSONObject, source: Source) {
    val extension = Extensions.byId(payload.optString("ext")) ?: return
    val action = payload.optString("action")
    val args = payload.optJSONArray("args") ?: JSONArray()
    val uuid = payload.optString("uuid")
    Log.d("Extension action ${extension.id}: $action")

    when (action) {
      "runtime.respond" -> {
        val callback = pending.remove(uuid) ?: return
        callback(payload.optBoolean("ok", true), payload.opt("value"))
      }
      "storage.get" -> {
        val area = args.optString(0, "local")
        send(
            source,
            uuid,
            true,
            Extensions.storage(extension.id).optJSONObject(area) ?: JSONObject())
      }
      "storage.set" -> {
        val area = args.optString(0, "local")
        val items = args.optJSONObject(1) ?: JSONObject()
        val storage = Extensions.storage(extension.id)
        val target = storage.optJSONObject(area) ?: JSONObject().also { storage.put(area, it) }
        items.keys().forEach { target.put(it, items.get(it)) }
        Extensions.saveStorage(extension.id)
        send(source, uuid, true, null)
      }
      "storage.remove" -> {
        val area = args.optString(0, "local")
        val target = Extensions.storage(extension.id).optJSONObject(area) ?: JSONObject()
        val keys =
            when (val value = args.opt(1)) {
              is JSONArray -> List(value.length()) { value.optString(it) }
              null -> emptyList()
              else -> listOf(value.toString())
            }
        keys.forEach { target.remove(it) }
        Extensions.saveStorage(extension.id)
        send(source, uuid, true, null)
      }
      "storage.clear" -> {
        Extensions.storage(extension.id).put(args.optString(0, "local"), JSONObject())
        Extensions.saveStorage(extension.id)
        send(source, uuid, true, null)
      }
      "runtime.sendMessage" -> {
        if (source.host) {
          send(source, uuid, false, "No receiving end for runtime.sendMessage")
          return
        }
        if (!extension.hasBackground) {
          send(source, uuid, false, "The extension has no background page")
          return
        }
        val sender = sender(extension, source)
        Background.evaluate(
            "__chromextHostDeliver(${JSONObject.quote(extension.id)}, ${jsonOf(args.opt(0))}," +
                " ${sender}, ${JSONObject.quote(uuid)});")
        pending[uuid] = { ok, value -> send(source, uuid, ok, value) }
      }
      "tabs.sendMessage" -> {
        val tabId = args.opt(0)?.toString() ?: ""
        val tab = findTab(tabId)
        val sender = sender(extension, source)
        val code =
            "(function(){var h=globalThis.__chromextHooks;" +
                "if(h&&h.deliver&&h.deliver[${JSONObject.quote(extension.id)}]){" +
                "h.deliver[${JSONObject.quote(extension.id)}](${jsonOf(args.opt(1))}, $sender," +
                " ${JSONObject.quote(uuid)});}else{" +
                "globalThis.__chromextResolve(${JSONObject.quote(uuid)}, false," +
                " \"No receiving end\");}})();"
        val delivered =
            if (tab != null) {
              deliver(tab, code)
              true
            } else {
              Chrome.evaluateJavascriptInTabId(tabId, code)
            }
        if (!delivered) {
          send(source, uuid, false, "Cannot evaluate in tab $tabId")
          return
        }
        pending[uuid] = { ok, value -> send(source, uuid, ok, value) }
      }
      "tabs.query" -> {
        val info = args.optJSONObject(0) ?: JSONObject()
        send(source, uuid, true, JSONArray(queryTabs(extension, info)))
      }
      "tabs.get" -> {
        val tab = findTab(args.opt(0)?.toString() ?: "")
        if (tab == null) send(source, uuid, false, "No tab found")
        else send(source, uuid, true, tabInfo(extension, tab))
      }
      "tabs.getCurrent" -> {
        val tab = queryTabs(extension, JSONObject().put("active", true)).firstOrNull()
        send(source, uuid, true, tab)
      }
      "tabs.create" -> {
        val info = args.optJSONObject(0) ?: JSONObject()
        val url = info.optString("url")
        val ok = openInNewTab(url)
        send(source, uuid, ok, if (ok) null else "Fail to open $url")
      }
      "tabs.update" -> {
        val tab = findTab(args.opt(0)?.toString() ?: "")
        val info = args.optJSONObject(1) ?: JSONObject()
        val url = info.optString("url")
        if (url.isNotEmpty()) {
          if (tab != null) deliver(tab, "location.href=${JSONObject.quote(url)};")
          else openInNewTab(url)
        }
        send(source, uuid, true, null)
      }
      "tabs.reload" -> {
        val tab = findTab(args.opt(0)?.toString() ?: "") ?: source.tab
        if (tab != null) deliver(tab, "location.reload();")
        send(source, uuid, true, null)
      }
      "tabs.remove" -> send(source, uuid, false, "tabs.remove is not supported yet")
      "tabs.executeScript",
      "tabs.insertCSS" -> {
        val tab = findTab(args.opt(0)?.toString() ?: "") ?: source.tab
        val details = args.optJSONObject(1) ?: JSONObject()
        val code = details.optString("code")
        if (tab != null && code.isNotEmpty()) deliver(tab, code)
        send(source, uuid, true, JSONArray())
      }
      "extension.getBackgroundPage" -> send(source, uuid, true, null)
      "permissions.contains" -> {
        val requested = args.optJSONObject(0) ?: JSONObject()
        val list = requested.optJSONArray("permissions")
        val granted =
            list == null ||
                (0 until list.length()).all {
                  extension.permissionWarnings.contains(list.optString(it))
                }
        send(source, uuid, true, granted)
      }
      "permissions.getAll" ->
          send(
              source,
              uuid,
              true,
              JSONObject().put("permissions", JSONArray(extension.permissionWarnings)))
      "permissions.request",
      "permissions.remove" -> send(source, uuid, true, true)
      "action.setBadgeText" -> {
        val details = args.optJSONObject(0) ?: JSONObject()
        badges[extension.id] = details.optString("text")
        Log.d("Badge of ${extension.id}: '${badges[extension.id]}'")
        send(source, uuid, true, null)
      }
      "action.setBadgeBackgroundColor",
      "action.setTitle",
      "action.setIcon",
      "action.setPopup",
      "action.enable",
      "action.disable" -> send(source, uuid, true, null)
      "contextMenus.create" -> {
        val details = args.optJSONObject(0) ?: JSONObject()
        val id = details.optString("id", "menu-${contextMenus.size}-${System.nanoTime()}")
        details.put("id", id)
        contextMenus.getOrPut(extension.id) { mutableListOf() }.add(details)
        send(source, uuid, true, id)
      }
      "contextMenus.update",
      "contextMenus.remove",
      "contextMenus.removeAll" -> send(source, uuid, true, null)
      "management.uninstallSelf" -> send(source, uuid, true, null)
      "notifications.create" -> send(source, uuid, false, "notifications API is not implemented")
      "alarms.create",
      "alarms.clear" -> send(source, uuid, true, null)
      "port.connect" -> {
        val portId = args.optString(0)
        ports[portId] = source
        if (!source.host) {
          val sender = sender(extension, source)
          Background.evaluate(
              "__chromextHostPortOpen(${JSONObject.quote(extension.id)}," +
                  " ${JSONObject.quote(portId)}, ${JSONObject.quote(args.optString(1))}, $sender);")
        }
        send(source, uuid, true, null)
      }
      "port.post" -> {
        val portId = args.optString(0)
        val message = jsonOf(args.opt(1))
        // The content side opened the port, the background page is always the other end
        if (source.host) {
          ports[portId]?.let { deliverToContent(it, portId, message) }
        } else {
          ports[portId] = source
          Background.evaluate(
              "__chromextHostPort(${JSONObject.quote(extension.id)}," +
                  " ${JSONObject.quote(portId)}, 'portMessage', $message);")
        }
        send(source, uuid, true, null)
      }
      "port.disconnect" -> {
        val portId = args.optString(0)
        val content = ports.remove(portId)
        if (source.host) {
          content?.let { deliverToContent(it, portId, null, disconnect = true) }
        } else {
          Background.evaluate(
              "__chromextHostPort(${JSONObject.quote(extension.id)}," +
                  " ${JSONObject.quote(portId)}, 'portDisconnect', null);")
        }
        send(source, uuid, true, null)
      }
      else -> {
        Log.d("Unsupported extension action of ${extension.id}: $action")
        send(source, uuid, false, "$action is not implemented by ChromeXt")
      }
    }
  }

  // ---- helpers ---------------------------------------------------------------

  /**
   * Evaluate code in a tab.
   *
   * A `javascript:` navigation is dropped when the tab is in the background, which is the usual
   * case for a message coming from the background page, so the DevTools session is preferred and
   * the injection channel only serves as a last resort.
   */
  private fun deliver(tab: Any, code: String) {
    val id = tabId(tab)
    if (id != 0 && Chrome.evaluateJavascriptInTabId(id.toString(), code)) return
    if (PageJs.evaluate(tab, code)) return
    Chrome.evaluateJavascript(listOf(code), tab)
  }

  private fun deliverToContent(
      source: Source,
      portId: String,
      message: String?,
      disconnect: Boolean = false,
  ) {
    val code =
        if (disconnect)
            "globalThis.__chromextPortDisconnect && globalThis.__chromextPortDisconnect(" +
                "${JSONObject.quote(portId)});"
        else
            "globalThis.__chromextPortMessage && globalThis.__chromextPortMessage(" +
                "${JSONObject.quote(portId)}, $message);"
    val tab = source.tab
    if (tab == null) Chrome.evaluateJavascript(listOf(code), null, source.frameId)
    else deliver(tab, code)
  }

  private fun sender(extension: Extension, source: Source): JSONObject {
    val json = JSONObject().put("id", extension.id)
    if (source.host) {
      json.put("url", Background.hostUrl())
      json.put("origin", ExtServer.origin(extension))
      return json
    }
    val url = runCatching { Chrome.getUrl(source.tab) }.getOrNull() ?: ""
    val tabId = tabId(source.tab)
    json.put(
        "tab",
        JSONObject()
            .put("id", tabId)
            .put("url", url)
            .put("active", true)
            .put("highlighted", true)
            .put("windowId", 1)
            .put("index", 0))
    json.put("frameId", 0)
    json.put("url", url)
    return json
  }

  private fun tabId(tab: Any?): Int {
    if (tab == null) return 0
    return runCatching { UserScriptProxy.getTabId(tab).toIntOrNull() }.getOrNull() ?: 0
  }

  private fun findTab(id: String): Any? {
    if (id.isEmpty()) return null
    val wanted = id.toIntOrNull() ?: return null
    return Chrome.tabList().firstOrNull { tabId(it) == wanted }
  }

  private fun queryTabs(extension: Extension, filter: JSONObject): List<JSONObject> {
    val tabs = Chrome.tabList()
    if (tabs.isNotEmpty()) {
      val activeTab = Chrome.getTab()
      return tabs
          .filter {
            if (filter.optBoolean("active") && it != activeTab) return@filter false
            if (filter.has("url")) {
              val url = runCatching { Chrome.getUrl(it) }.getOrNull() ?: ""
              if (!MatchPattern.matches(filter.optString("url"), url)) return@filter false
            }
            true
          }
          .map { tabInfo(extension, it) }
    }
    // Reading the DevTools endpoint blocks, so it is not used on the dispatch path
    return emptyList()
  }

  private fun pageInfo(page: JSONObject): JSONObject =
      JSONObject()
          .put("id", page.optString("id").toIntOrNull() ?: 0)
          .put("url", page.optString("url"))
          .put("title", page.optString("title"))
          .put("active", page.optBoolean("attached"))
          .put("highlighted", page.optBoolean("attached"))
          .put("windowId", 1)
          .put("index", 0)
          .put("status", "complete")
          .put("incognito", false)

  private fun tabInfo(extension: Extension, tab: Any): JSONObject {
    val url = runCatching { Chrome.getUrl(tab) }.getOrNull() ?: ""
    val title = tabTitle(tab)
    return JSONObject()
        .put("id", tabId(tab))
        .put("url", url)
        .put("title", title)
        .put("active", tab == Chrome.getTab())
        .put("highlighted", tab == Chrome.getTab())
        .put("windowId", 1)
        .put("index", 0)
        .put("status", "complete")
        .put("incognito", false)
  }

  private fun tabTitle(tab: Any): String =
      runCatching { tab.javaClass.getMethod("getTitle").invoke(tab) as? String }.getOrNull() ?: ""

  private fun openInNewTab(url: String): Boolean {
    if (url.isEmpty()) return false
    return runCatching {
          val intent =
              Intent(Intent.ACTION_VIEW, Uri.parse(url))
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  .setPackage(Chrome.packageName)
          Chrome.getContext().startActivity(intent)
          true
        }
        .onFailure { Log.ex(it, "Fail to open $url") }
        .getOrDefault(false)
  }

  fun menuItems(extension: String): List<JSONObject> = contextMenus[extension] ?: emptyList()

  fun badge(extension: String): String = badges[extension] ?: ""
}
