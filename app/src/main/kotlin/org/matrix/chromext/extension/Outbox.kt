package org.matrix.chromext.extension

import org.json.JSONArray
import org.json.JSONObject

/**
 * Commands waiting for a content script, picked up by its poll loop.
 *
 * Pushing into a page is only reliable for the tab that is asking, so every delivery to a content
 * script is queued here and the script collects it. This also keeps the extension runtime away from
 * the DevTools endpoint, whose HTTP handler serves a single client and then stops answering.
 */
object Outbox {

  private val queues = mutableMapOf<String, MutableList<JSONObject>>()

  private fun key(extension: String, tab: Int) = "$extension@$tab"

  fun push(extension: String, tab: Int, item: JSONObject) {
    if (tab == 0) return
    synchronized(queues) { queues.getOrPut(key(extension, tab)) { mutableListOf() }.add(item) }
  }

  fun drain(extension: String, tab: Int): JSONArray {
    val items =
        synchronized(queues) {
          val queue = queues.remove(key(extension, tab))
          queue?.toList() ?: return JSONArray()
        }
    val array = JSONArray()
    items.forEach { array.put(it) }
    return array
  }

  fun clear(tab: Int) {
    synchronized(queues) { queues.keys.removeAll { it.endsWith("@$tab") } }
  }

  fun clearAll() {
    synchronized(queues) { queues.clear() }
  }
}
