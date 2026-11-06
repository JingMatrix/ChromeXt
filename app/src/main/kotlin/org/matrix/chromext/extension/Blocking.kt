package org.matrix.chromext.extension

import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.utils.Log

/**
 * Request filtering, as far as a Java hook can go.
 *
 * Chrome fetches page sub-resources in the network service, in C++, and ships no Java HTTP layer at
 * all, so nothing on this side can see or stop an image, script or XHR. What is reachable is the
 * top level navigation: `declarativeNetRequest` rules and `webRequest` main_frame listeners are
 * therefore applied to the page the tab is about to load, and nothing else.
 *
 * A rule "blocks" by stopping the load, which keeps the previous page (Chrome has no error page for
 * us to show), and "redirects" by navigating the tab to the target.
 */
object Blocking {

  sealed class Decision {
    object Allow : Decision()

    class Block(val extension: String, val ruleId: Int, val url: String) : Decision()

    class Redirect(val extension: String, val from: String, val url: String) : Decision()
  }

  private const val DYNAMIC_KEY = "__dnrDynamic"

  private class Rule(
      val id: Int,
      val priority: Int,
      val action: String,
      val urlRegex: Regex?,
      val redirectUrl: String?,
      val resourceTypes: List<String>,
      val excludedResourceTypes: List<String>,
      val methods: List<String>,
      val domains: List<String>,
      val excludedDomains: List<String>,
  ) {
    fun matches(url: String, method: String): Boolean {
      val types = resourceTypes
      if (types.isNotEmpty() && !types.contains("main_frame")) return false
      if (excludedResourceTypes.contains("main_frame")) return false
      if (methods.isNotEmpty() && !methods.contains(method)) return false
      val host = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
      if (domains.isNotEmpty() && domains.none { hostMatches(it, host) }) return false
      if (excludedDomains.any { hostMatches(it, host) }) return false
      return urlRegex?.containsMatchIn(url) ?: false
    }

    private fun hostMatches(rule: String, host: String): Boolean =
        if (rule.startsWith("~")) false
        else if (rule.startsWith("*."))
            host == rule.substring(2) || host.endsWith(rule.substring(1))
        else host == rule
  }

  /** A `webRequest` listener registered by an extension. */
  private class Listener(
      val extension: String,
      val kind: String,
      val urls: List<String>,
      val types: List<String>,
      val blocking: Boolean,
  )

  private val listeners = mutableListOf<Listener>()
  private val cache = mutableMapOf<String, Pair<Long, List<Rule>>>()
  private var navigationId = 0
  private val pending = mutableMapOf<Int, Pair<Any, String>>()

  fun register(extension: String, kind: String, filter: JSONObject, spec: JSONArray) {
    val urls =
        filter.optJSONArray("urls")?.let { array ->
          (0 until array.length()).mapNotNull { array.optString(it).ifEmpty { null } }
        } ?: emptyList()
    val types =
        filter.optJSONArray("types")?.let { array ->
          (0 until array.length()).mapNotNull { array.optString(it).ifEmpty { null } }
        } ?: emptyList()
    val blocking = (0 until spec.length()).any { spec.optString(it) == "blocking" }
    listeners.removeAll(listeners.filter { it.extension == extension && it.kind == kind })
    listeners.add(Listener(extension, kind, urls, types, blocking))
    Log.d("webRequest: $extension listens on $kind blocking=$blocking urls=$urls")
  }

  fun forget(extension: String) {
    listeners.removeAll(listeners.filter { it.extension == extension })
  }

  /** Rules of an extension: static rulesets from the manifest plus the dynamic ones it added. */
  private fun rulesOf(extension: Extension): List<Rule> {
    val dynamic = Extensions.storage(extension.id).optJSONArray(DYNAMIC_KEY)?.toString() ?: ""
    val stamp = dynamic.hashCode().toLong()
    cache[extension.id]?.let { (cached, rules) -> if (cached == stamp) return rules }
    val rules = mutableListOf<Rule>()
    extension.dnrRuleResources.forEach { path ->
      extension.readText(path)?.let { text ->
        runCatching {
          val array = JSONArray(text)
          (0 until array.length())
              .mapNotNull { array.optJSONObject(it) }
              .forEach { parse(it)?.let(rules::add) }
        }
            .onFailure { Log.e("Cannot read the ruleset $path of ${extension.id}: ${it.message}") }
      }
    }
    runCatching {
      val array = JSONArray(dynamic)
      (0 until array.length())
          .mapNotNull { array.optJSONObject(it) }
          .forEach { parse(it)?.let(rules::add) }
    }
        .onFailure { Log.d("No dynamic rules for ${extension.id}") }
    cache[extension.id] = stamp to rules
    return rules
  }

  private fun parse(json: JSONObject): Rule? {
    val id = json.optInt("id", 0)
    val action = json.optJSONObject("action") ?: return null
    val condition = json.optJSONObject("condition") ?: return null
    val filter = condition.optString("urlFilter").ifEmpty { null }
    val regex =
        condition
            .optString("regexFilter")
            .ifEmpty { null }
            ?.let { runCatching { Regex(it) }.getOrNull() } ?: filter?.let { filterToRegex(it) }
    if (regex == null) return null
    return Rule(
        id = id,
        priority = json.optInt("priority", 1),
        action = action.optString("type", "block"),
        urlRegex = regex,
        redirectUrl = action.optJSONObject("redirect")?.optString("url"),
        resourceTypes = condition.optJSONArray("resourceTypes")?.toStringList() ?: emptyList(),
        excludedResourceTypes =
            condition.optJSONArray("excludedResourceTypes")?.toStringList() ?: emptyList(),
        methods = condition.optJSONArray("requestMethods")?.toStringList() ?: emptyList(),
        domains = condition.optJSONArray("domains")?.toStringList() ?: emptyList(),
        excludedDomains = condition.optJSONArray("excludedDomains")?.toStringList() ?: emptyList(),
    )
  }

  /** `||host^`, `|url|`, `*` and `^` from the declarativeNetRequest filter syntax. */
  private fun filterToRegex(filter: String): Regex? {
    var rest = filter
    val sb = StringBuilder()
    when {
      rest.startsWith("||") -> {
        sb.append("^[a-z][a-z0-9+.\\-]*://(?:[^/?#]*\\.)?")
        rest = rest.substring(2)
      }
      rest.startsWith("|") -> {
        sb.append("^")
        rest = rest.substring(1)
      }
    }
    var anchored = false
    if (rest.endsWith("|")) {
      anchored = true
      rest = rest.dropLast(1)
    }
    rest.forEach { c ->
      when (c) {
        '*' -> sb.append(".*")
        '^' -> sb.append("(?:[^a-zA-Z0-9_.%\\-]|$)")
        else -> sb.append(Regex.escape(c.toString()))
      }
    }
    if (anchored) sb.append("$")
    return runCatching { Regex(sb.toString(), RegexOption.IGNORE_CASE) }.getOrNull()
  }

  private fun decide(url: String): Decision {
    var best: Triple<String, Rule, Int>? = null
    Extensions.enabled().forEach { extension ->
      rulesOf(extension).forEach { rule ->
        if (!rule.matches(url, "GET")) return@forEach
        val current = best
        if (current == null || rule.priority > current.third) {
          best = Triple(extension.id, rule, rule.priority)
        }
      }
    }
    val (extension, rule, _) = best ?: return Decision.Allow
    return when (rule.action) {
      "block" -> Decision.Block(extension, rule.id, url)
      "redirect" ->
          rule.redirectUrl?.let { Decision.Redirect(extension, url, it) } ?: Decision.Allow
      else -> Decision.Allow
    }
  }

  /**
   * Apply the filters to a navigation about to happen.
   *
   * Returns true when the navigation was taken over, i.e. the caller must not inject into it.
   */
  fun enforce(tab: Any, url: String): Boolean {
    when (val decision = decide(url)) {
      is Decision.Block -> {
        Log.i("${decision.extension} blocks ${decision.url}")
        UserScriptProxy.stopLoading(tab)
        Log.toast(Chrome.getContext(), "ChromeXt: 已按 ${decision.extension} 的规则拦截该页面")
        return true
      }
      is Decision.Redirect -> {
        Log.i("${decision.extension} redirects ${decision.from} to ${decision.url}")
        UserScriptProxy.navigate(tab, decision.url)
        return true
      }
      Decision.Allow -> {}
    }
    askListeners(tab, url)
    return false
  }

  private fun askListeners(tab: Any, url: String) {
    val interested = listeners.filter {
      it.kind == "onBeforeRequest" &&
          (it.types.isEmpty() || it.types.contains("main_frame")) &&
          (it.urls.isEmpty() || it.urls.any { pattern -> MatchPattern.matches(pattern, url) })
    }
    if (interested.isEmpty()) return
    val id = ++navigationId
    pending[id] = tab to url
    val details =
        JSONObject()
            .put("requestId", id.toString())
            .put("url", url)
            .put("method", "GET")
            .put("type", "main_frame")
            .put("frameId", 0)
            .put(
                "tabId",
                runCatching { UserScriptProxy.getTabId(tab).toIntOrNull() }.getOrNull() ?: 0)
            .put("timeStamp", System.currentTimeMillis())
    interested
        .map { it.extension }
        .distinct()
        .forEach { extension ->
          Extensions.byId(extension)?.let { host ->
            if (!host.hasBackground) return@forEach
            Background.evaluate(
                "__chromextHostWebRequest(${JSONObject.quote(extension)}," +
                    " ${JSONObject.quote("onBeforeRequest")}, $details, ${JSONObject.quote(id.toString())});")
          }
        }
    // Cancelling still helps after the fact: the load gets stopped, the page never renders
    Chrome.IO.submit {
      Thread.sleep(1500)
      pending.remove(id)
    }
  }

  /** Called when an extension answers a main_frame `onBeforeRequest`. */
  fun onAnswer(requestId: String, cancel: Boolean) {
    val id = requestId.toIntOrNull() ?: return
    if (!cancel) {
      pending.remove(id)
      return
    }
    pending.remove(id)?.let { (tab, url) ->
      Log.i("A webRequest listener cancelled $url")
      UserScriptProxy.stopLoading(tab)
    }
  }

  /** Called when an extension answers with a redirect target. */
  fun onRedirect(requestId: String, url: String) {
    val id = requestId.toIntOrNull() ?: return
    if (url.isEmpty()) return
    pending.remove(id)?.let { (tab, from) ->
      Log.i("A webRequest listener redirects $from to $url")
      UserScriptProxy.navigate(tab, url)
    }
  }

  fun hasListeners(extension: String): Boolean = listeners.any { it.extension == extension }
}
