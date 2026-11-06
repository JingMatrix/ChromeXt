package org.matrix.chromext.extension

import org.matrix.chromext.utils.Log

/**
 * The extension background pages, which run in ChromeXt's own process (see [HostService]).
 *
 * Commands are queued here and collected by the host page through `/_api/poll`, because the page
 * runs in another process and can only be reached over the loopback server.
 */
object Background {

  private var ready = false
  private var binding = false

  private fun alive(): Boolean = ready && System.currentTimeMillis() - ExtServer.lastPollAt < 15000

  fun isReady(): Boolean = alive()

  fun status(): String =
      when {
        alive() -> "running"
        binding -> "starting"
        else -> "stopped"
      }

  /** Make sure the host process is up. Never blocks. */
  fun ensure() {
    if (ready || binding) return
    binding = true
    val started = HostClient.ensure(ExtServer.hostUrl())
    if (!started) binding = false
    // The page announces itself once it has loaded, see onHostReady()
    if (ready) binding = false
  }

  /** Queue code for the host page. */
  fun evaluate(code: String): Boolean {
    ensure()
    // A frozen host page stops polling: poke it so the command is picked up
    if (System.currentTimeMillis() - ExtServer.lastPollAt > 4000) {
      HostClient.poke(ExtServer.hostUrl())
    }
    HostQueue.push(code)
    return true
  }

  fun onHostReady() {
    if (!ready) Log.i("Extension host page is ready")
    ready = true
    binding = false
  }

  fun stop() {
    ready = false
    binding = false
    HostClient.release()
  }
}
