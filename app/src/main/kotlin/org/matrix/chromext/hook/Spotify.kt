package org.matrix.chromext.hook

import de.robv.android.xposed.XC_MethodHook.Unhook
import org.matrix.chromext.utils.*

fun ByteArray.toPrintableString() = filter {
  it in 32..126
}.map { it.toInt().toChar() }.joinToString("")

fun ByteArray.toHexString() = joinToString(" ") { "%02x".format(it) }

object SpotifyHook : BaseHook() {
  var loader = this::class.java.classLoader!!

  // Verbose traffic/diagnostic logging: the esperanto (RxJava/coroutine/native)
  // RPCs, the cosmos router, HTTP, and the PlayerError / Ravelin probes. Off for
  // normal use; flip on to investigate what the app/server actually exchange.
  private const val DEBUG_LOG = false

  private fun load(name: String): Class<*> = loader.loadClass(name)

  // Run one independent hook unit. A failure -- e.g. a class or method renamed on a
  // newer Spotify build -- is logged and skipped, never aborting the hooks after it.
  // (An unguarded loadClass for a class removed on 9.1.72 once silently disabled the
  // entire second half of this module.)
  private inline fun feature(name: String, block: () -> Unit) =
      try {
        block()
      } catch (t: Throwable) {
        Log.d("Spotify: skipped feature '$name': $t")
      }

  override fun init() {
    feature("product-state") { spoofProductState() }
    feature("native-session") { spoofNativeSession() }
    feature("artist-track-rows") { enableArtistTrackRows() }
    feature("context-menu-upsell") { hideContextMenuUpsell() }
    feature("voice-assistant") { relaxVoiceAssistant() }
    feature("home-ad-sections") { stripAdSections() }
    feature("premium-upsell-message") { blockPremiumUpsellMessage() }
    feature("ads-rpc-block") { blockAdsRpc() }
    feature("prevent-forced-logout") { preventForcedLogout() }
    feature("play-integrity") { bypassPlayIntegrity() }
    feature("unlock-jam") { unlockJam() }
    if (DEBUG_LOG) installDiagnostics()
    isInit = true
  }

  // Remove the premium-gated "Start a Jam" entry from the track context menu. The
  // feature is server-locked for a free account, so the item is dead weight.
  //
  // We deliberately avoid naming the obfuscated provider class (it is renamed every
  // release) and anchor on the one value that is part of a stable contract: the item
  // id "jam_start". The menu collects its item providers into a container whose
  // constructor iterates them and asks each to build its item model; the Jam model
  // carries "jam_start" in a String field. We drop the provider that builds it from
  // the list -- the container and the downstream aggregator both read the trimmed
  // list, so the entry never materialises. (Nulling the built item instead leaves the
  // aggregator to dereference a null and the whole menu fails to open.)
  //
  // The container class ("p.fti" here) is the one unavoidable anchor: this UI has no
  // stable supertype or API boundary to reach it through, so its obfuscated name is
  // hardcoded and will change on a Spotify bump. feature() keeps such a rename from
  // taking anything else down with it. To re-derive the name on a new version, from
  // the decompiled smali (apktool):
  //   1. grep for the string  "jam_start"  -> lands in the Jam provider's builder,
  //      e.g.  p.lhq#c()Lp/jri;  , which implements the item interface  p.kri
  //      (abstract  c()Lp/jri; ).
  //   2. grep for  Lp/kri;->c()Lp/jri;  and pick the class that iterates a List
  //      calling it and keeps that List in a field -- its constructor is
  //      <init>(_, List, boolean); that class (field b = provider list) is the
  //      container to load below.
  private fun unlockJam() {
    load("p.fti")
        .declaredConstructors
        .filter { it.parameterTypes.size == 3 && it.parameterTypes[1] == List::class.java }
        .forEach {
          it.isAccessible = true
          it.hookBefore { param ->
            val providers = param.args[1] as? List<*> ?: return@hookBefore
            val trimmed = providers.filterNot { p -> p != null && buildsJamItem(p) }
            if (trimmed.size != providers.size) param.args[1] = trimmed
          }
        }
  }

  // True if this context-menu item provider builds the Jam entry. A provider exposes
  // no-arg builders that realise item models; the Jam model carries the id "jam_start"
  // in a String field. Probing the builders here is safe -- the container constructor
  // we hook calls the very same builders on the very same list a moment later.
  private fun buildsJamItem(provider: Any): Boolean =
      provider::class.java.declaredMethods.any { m ->
        m.parameterTypes.isEmpty() &&
            !m.returnType.isPrimitive &&
            m.returnType != String::class.java &&
            runCatching {
                  m.isAccessible = true
                  val item = m.invoke(provider) ?: return@runCatching false
                  item::class.java.declaredFields.any { f ->
                    f.type == String::class.java &&
                        runCatching {
                              f.isAccessible = true
                              f.get(item) == "jam_start"
                            }
                            .getOrDefault(false)
                  }
                }
                .getOrDefault(false)
      }

  // ---------------------------------------------------------------------------
  // Premium / product state
  // ---------------------------------------------------------------------------

  // Overrides applied to the canonical ProductStateProto values.
  private val premiumState =
      mapOf(
          "ads" to false, // disable player/app ads
          "player-license" to "premium", // play anything, no restriction
          "shuffle" to false, // don't force shuffle when starting a playlist
          "on-demand" to true, // on-demand playback, unshuffled
          "type" to "premium", // premium UI, Spotify Connect, no buy-premium
          "streaming" to true, // never disable streaming remotely
          "pick-and-shuffle" to false, // allow queueing, any play mode
          "streaming-rules" to "", // drop shuffle-mode streaming rule
          "nft-disabled" to "1", // premium settings UI, hide nav-bar premium button
          "can_use_superbird" to true, // Spotify Car Thing
          "tablet-free" to false, // hide nav-bar premium button on tablets
          "smart-shuffle" to "AVAILABLE", // don't force smart-shuffle; allow any mode
      )

  // Spoof premium at its source: ProductStateProto.values_ is a LinkedHashMap of
  // key -> AccountAttribute, whose value_ is Boolean/Long/String selected by
  // valueCase_. This is the map the entitlement UI reads; the flat-map hook below
  // never reaches it (its values are AccountAttribute, not String). Coerce each
  // override to the attribute's existing runtime type so later reads stay type-safe.
  private fun spoofProductState() {
    val productStateProto = load("com.spotify.remoteconfig.internal.ProductStateProto")
    val values = findField(productStateProto) { name == "values_" }
    val attributeValue =
        findField(load("com.spotify.remoteconfig.internal.AccountAttribute")) { name == "value_" }

    fun coerce(current: Any?, override: Any): Any =
        when (current) {
          is Boolean ->
              when (override) {
                is Boolean -> override
                is Number -> override.toInt() != 0
                else -> override.toString().let { it == "1" || it.equals("true", true) }
              }
          is Long ->
              when (override) {
                is Number -> override.toLong()
                is Boolean -> if (override) 1L else 0L
                else -> override.toString().toLongOrNull() ?: 0L
              }
          else -> override.toString()
        }

    @Suppress("UNCHECKED_CAST")
    findMethod(productStateProto) { returnType == productStateProto && parameterTypes.size == 1 }
        .hookAfter {
          val state = values.get(it.result) as Map<String, Any>
          for ((key, value) in premiumState) {
            val attribute = state[key] ?: continue
            attributeValue.set(attribute, coerce(attributeValue.get(attribute), value))
          }
        }
  }

  // Inject premium attributes into the native session bootstrap map.
  private fun spoofNativeSession() {
    val overrides =
        mapOf(
            "ads" to "0",
            "player-license" to "premium",
            "pick-and-shuffle" to "0",
            "on-demand" to "1",
            "streaming-rules" to "",
            "smart-shuffle" to "AVAILABLE",
        )
    findMethod(load("com.spotify.connectivity.auth.NativeSession")) {
          name == "createNativeSessionWithoutAp"
        }
        .hookBefore {
          @Suppress("UNCHECKED_CAST") (it.args[3] as MutableMap<String, String>).putAll(overrides)
        }
  }

  // ---------------------------------------------------------------------------
  // Player / UI tweaks
  // ---------------------------------------------------------------------------

  // Show the track list on the artist page by forcing the trackRows capability.
  private fun enableArtistTrackRows() {
    val preparePlayOptions = load("com.spotify.player.model.command.options.PreparePlayOptions")
    val option = findMethod(preparePlayOptions) { name == "configurationOverride" }.returnType
    val field = "checkDeviceCapability"
    @Suppress("UNCHECKED_CAST")
    findMethod(option) { name == "containsKey" }
        .hookAfter {
          if (it.args[0] == "signal" && it.thisObject.toString().contains("$field=")) {
            val store =
                findField(it.thisObject::class.java) { type == Array::class.java }
                    .get(it.thisObject) as Array<Any>
            val index = store.indexOfLast { e -> e == field || e == "trackRows" }
            store[index] = "trackRows"
            store[index + 1] = true
          }
        }
  }

  // Hide the ads/jam upsell entries in the track context menu. The obfuscated
  // PropertyParser is discovered via a stable `*Properties` trampoline, then its
  // boolean (category, key, default) lookup is patched.
  private fun hideContextMenuUpsell() {
    var finder: Unhook? = null
    finder =
        findMethod(load("com.spotify.localfiles.mediastoreimpl.LocalFilesProperties")) {
              name == "parse"
            }
            .hookAfter {
              finder?.unhook()
              findMethod(it.args[0]::class.java) {
                    parameterTypes contentDeepEquals
                        arrayOf(String::class.java, String::class.java, Boolean::class.java) &&
                        returnType == Boolean::class.java
                  }
                  .hookAfter { p ->
                    val category = p.args[0]
                    val key = p.args[1]
                    if ((category == "android-context-menu" &&
                        key == "remove_ads_upsell_enabled") ||
                        (category == "android-libs-social-listening" &&
                            key == "enable_jam_upsell_in_context_menu_item")) {
                      p.result = false
                    }
                  }
            }
  }

  // Remove voice-assistant restrictions: strip the "station:" prefix so VA plays the
  // real context, and force shufflingContext off.
  private fun relaxVoiceAssistant() {
    val context = load("com.spotify.player.model.Context")
    val autoValueContext = load("com.spotify.player.model.AutoValue_Context")
    val url = findField(autoValueContext) { name == "url" }
    val uri = findField(autoValueContext) { name == "uri" }
    findMethod(load("com.spotify.voiceassistants.playermodels.ContextJsonAdapter")) {
          name == "fromJson" && returnType == context
        }
        .hookAfter {
          url.set(it.result, (url.get(it.result) as String).replace("station:", ""))
          uri.set(it.result, (uri.get(it.result) as String).replace("station:", ""))
        }

    val preparePlayOptions = load("com.spotify.player.model.command.options.PreparePlayOptions")
    val playerOptionsOverride =
        findField(load("com.spotify.player.model.command.options.AutoValue_PreparePlayOptions")) {
          name == "playerOptionsOverride"
        }
    val shufflingContext =
        findField(
            load("com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides")) {
              name == "shufflingContext"
            }
    findMethod(load("com.spotify.voiceassistants.playermodels.PreparePlayOptionsJsonAdapter")) {
          name == "fromJson" && returnType == preparePlayOptions
        }
        .hookAfter {
          val override = playerOptionsOverride.get(it.result)
          val optionOverrides =
              findField(override::class.java) { type == Any::class.java }.get(override)
          val shuffling = shufflingContext.get(optionOverrides)
          findField(shuffling::class.java) { type == Any::class.java }.set(shuffling, false)
        }
  }

  // Remove brand-ad sections from the resolved home and browse feeds. Only genuine
  // ad section types are dropped; the ProtobufArrayList is made mutable first.
  private fun stripAdSections() {
    data class Page(
        val structure: String,
        val section: String,
        val field: String,
        val toRemove: Set<String>
    )
    val pages =
        listOf(
            Page(
                "com.spotify.casita.v1.resolved.HomeStructure",
                "com.spotify.casita.v1.resolved.Section",
                "featureTypeCase_",
                setOf(
                    "IMAGE_BRAND_AD_FIELD_NUMBER",
                    "PREVIEW_FIELD_NUMBER",
                    "VIDEO_BRAND_AD_FIELD_NUMBER",
                )),
            Page(
                "com.spotify.browsita.v1.resolved.BrowseStructure",
                "com.spotify.browsita.v1.resolved.Section",
                "sectionTypeCase_",
                setOf("BRAND_ADS_FIELD_NUMBER")))
    for (page in pages) feature("ad-sections:${page.structure.substringAfterLast('.')}") {
      val structure = load(page.structure)
      val section = load(page.section)
      val sections = findField(structure) { name == "sections_" }
      val featureType = findField(section) { name == page.field }
      val sectionTypes =
          section.declaredFields
              .filter { it.name.endsWith("_FIELD_NUMBER") }
              .associate { f ->
                f.isAccessible = true
                f.get(null) to f.name
              }
      findMethod(structure) { returnType == sections.type }
          .hookBefore {
            @Suppress("UNCHECKED_CAST") val list = sections.get(it.thisObject) as MutableList<Any>
            findField(list::class.java, true) { type == Boolean::class.java }.set(list, true)
            list.removeIf { s -> page.toRemove.contains(sectionTypes[featureType.get(s)]) }
          }
    }
  }

  // Disallow purchases on upsell client-messaging requests so premium upsells drop.
  private fun blockPremiumUpsellMessage() {
    val fetchMessageRequest =
        load(
            "com.spotify.messaging.clientmessagingplatform.clientmessagingplatformsdk.data.models.network.FetchMessageRequest")
    val entityUri = findField(fetchMessageRequest) { name == "entityUri" }
    val purchaseAllowed = findField(fetchMessageRequest) { name == "purchaseAllowed" }
    findMethod(fetchMessageRequest) { name == "getOpportunityId" }
        .hookAfter {
          if ((entityUri.get(it.thisObject) as String?)?.startsWith("upsell") == true) {
            purchaseAllowed.set(it.thisObject, false)
          }
        }
  }

  // ---------------------------------------------------------------------------
  // Server-facing blocks
  // ---------------------------------------------------------------------------

  // Prevent server-driven forced logout. The client subscribes to the native
  // Session/willLogoutAndForgetCurrentUser event via esperanto callStream (matched
  // by the stable method name -- no obfuscated class); returning a never-emitting
  // Observable keeps the subscription alive but eventless, so a flagged/repackaged
  // session is never force-logged-out. User-initiated logout uses a different path.
  private fun preventForcedLogout() {
    val never =
        findMethod(load("io.reactivex.rxjava3.core.Observable")) {
              name == "never" && parameterTypes.isEmpty()
            }
            .invoke(null)
    findMethod(load("com.spotify.esperanto.esperanto.ClientBase")) { name == "callStream" }
        .hookBefore {
          if (it.args[1] == "willLogoutAndForgetCurrentUser") {
            Log.d("Spotify: suppressed forced logout")
            it.result = never
          }
        }
  }

  // Fail the ad-related esperanto RPCs (and the spoofed-state legacy writeback).
  private fun blockAdsRpc() {
    val single = load("io.reactivex.rxjava3.core.Single")
    val errorSingle =
        findMethod(single) {
          name == "error" && parameterTypes.size == 1 && parameterTypes[0] == Throwable::class.java
        }
    findMethod(load("com.spotify.esperanto.esperanto.ClientBase")) { name == "callSingle" }
        .hookBefore {
          val service = it.args[0] as String
          val method = it.args[1] as String
          if (service.startsWith("spotify.ads") || method == "writeProductStateToLegacyStorage") {
            it.result = errorSingle.invoke(null, Exception("Blocked via ChromeXt"))
          }
        }
  }

  // Bypass the Play Integrity attestation (spotify.pitoken.v1.VerifyService).
  //
  // Spotify obtains a Google Play StandardIntegrity token and POSTs it, via a Retrofit
  // webgate service, to pitoken/spotify.pitoken.v1.VerifyService/Verify as
  // VerifyRequest -> VerifyResponse. After LSPatch repackages the APK the token's app
  // verdict no longer matches Spotify's Play listing, so the backend can flag the
  // session. The token cannot be forged, and the client never reads the response body,
  // so the fix is to never send the mismatched token to the backend.
  //
  // Reached only through stable anchors (no obfuscated names): hook RetrofitMaker's
  // service factory, recognise the integrity service by its method that takes a
  // VerifyRequest, and swap Retrofit's proxy for one that answers Verify locally with
  // an empty VerifyResponse -- skipping the HTTP call entirely.
  private fun bypassPlayIntegrity() {
    val verifyRequest = load("com.spotify.integrity.integrityimpl.proto.VerifyRequest")
    val verifyResponse = load("com.spotify.integrity.integrityimpl.proto.VerifyResponse")
    val emptyResponse = findField(verifyResponse) { name == "DEFAULT_INSTANCE" }.get(null)

    findMethod(load("com.spotify.connectivity.httpretrofit.RetrofitMaker")) {
          name == "createWebgateService" && parameterTypes.size == 1
        }
        .hookAfter {
          val service = it.args[0] as Class<*>
          val isVerifyService =
              service.declaredMethods.any { m -> m.parameterTypes.firstOrNull() == verifyRequest }
          if (!isVerifyService) return@hookAfter
          val real = it.result
          it.result =
              java.lang.reflect.Proxy.newProxyInstance(service.classLoader, arrayOf(service)) {
                  _,
                  method,
                  args ->
                // Verify(VerifyRequest, Continuation): a suspend fun may return its
                // result directly instead of suspending -- hand back the empty response.
                if (method.parameterTypes.firstOrNull() == verifyRequest) emptyResponse
                else method.invoke(real, *(args ?: emptyArray()))
              }
        }
  }

  // ---------------------------------------------------------------------------
  // Diagnostics (DEBUG_LOG only)
  // ---------------------------------------------------------------------------

  private fun installDiagnostics() {
    feature("log-cosmos") { logCosmos() }
    feature("log-esperanto") { logEsperanto() }
    feature("log-http") { logHttp() }
    feature("probe-player-error") { probePlayerError() }
    feature("probe-ravelin") { probeRavelin() }
    feature("probe-actions") { probeActionLayers() }
  }

  // Log the raw request+response bytes (text + hex) of the actionable cosmos services
  // so we can read the real verdict/consumption values behind the protobuf.
  private fun probeActionLayers() {
    val watch =
        listOf(
            "CanPlayContent",
            "CanAddToQueue",
            "CommonCapping",
            "Capping",
            "OnDemandSet",
            "on_demand_set",
            "CanPlay")
    val response = load("com.spotify.cosmos.cosmos.Response")
    val uri = findField(response) { name == "uri" }
    val body = findField(response) { name == "body" }
    findMethod(load("com.spotify.cosmos.routercallback.ResolverCallbackReceiver")) {
          name == "sendOnResolved"
        }
        .hookBefore {
          val u = uri.get(it.args[0]) as String
          if (watch.any { w -> u.contains(w) }) {
            val b = body.get(it.args[0]) as ByteArray
            Log.d(
                "ACTION ${u.substringAfterLast('.')} | txt=${b.toPrintableString().take(160)}" +
                    " | hex=${b.toHexString().take(360)}")
          }
        }
    // The esperanto REQUEST side for the same services (the queried track uris).
    val toBytes = protoToBytes()
    findMethod(load("com.spotify.esperanto.esperanto.CoroutineClientBase")) { name == "callSingle" }
        .hookBefore {
          val m = it.args[1] as String
          if (watch.any { w -> m.contains(w) }) {
            val b = toBytes(it.args[2])
            Log.d(
                "ACTION req $m | txt=${b.toPrintableString().take(160)} | hex=${b.toHexString().take(240)}")
          }
        }
  }

  // Serialize an esperanto proto payload argument to bytes.
  private fun protoToBytes(): (Any?) -> ByteArray {
    val protoType =
        findMethod(load("com.google.protobuf.Empty")) { name == "getDefaultInstanceForType" }
            .returnType
    val toByteArray = findMethod(protoType) { name == "toByteArray" }
    return { message -> toByteArray.invoke(message) as ByteArray }
  }

  // The cosmos request router: sp:// service responses (product state, player,
  // permissions, playlists, ...).
  private fun logCosmos() {
    val response = load("com.spotify.cosmos.cosmos.Response")
    val status = findField(response) { name == "status" }
    val uri = findField(response) { name == "uri" }
    val body = findField(response) { name == "body" }
    findMethod(load("com.spotify.cosmos.routercallback.ResolverCallbackReceiver")) {
          name == "sendOnResolved"
        }
        .hookBefore {
          val res = it.args[0]
          Log.d(
              "COSMOS [${status.get(res)}] ${uri.get(res)} | " +
                  (body.get(res) as ByteArray).toPrintableString().take(600))
        }
  }

  // The esperanto native<->Java bridge across all three client flavours. RxJava and
  // coroutine clients pass proto payloads; NativeTransport passes raw bytes and is the
  // single point both funnel through to orbit (dealer WebSocket push arrives here too).
  private fun logEsperanto() {
    val toBytes = protoToBytes()
    val clients =
        listOf(
            Triple("com.spotify.esperanto.esperanto.ClientBase", "RX", false),
            Triple("com.spotify.esperanto.esperanto.CoroutineClientBase", "CO", false),
            Triple("com.spotify.esperanto.esperantoimpl.NativeTransport", "NATIVE", true))
    for ((className, tag, raw) in clients) feature("log-esperanto:$tag") {
      val client = load(className)
      for (m in listOf("callSingle", "callStream", "callSync")) {
        findMethodOrNull(client) { name == m }
            ?.hookBefore {
              val payload = if (raw) it.args[2] as ByteArray else toBytes(it.args[2])
              Log.d(
                  "$tag $m ${it.args[0]}/${it.args[1]} | ${payload.toPrintableString().take(600)}")
            }
      }
    }
  }

  // All OkHttp webgate/image traffic. Every call funnels through
  // RealCall.getResponseWithInterceptorChain() (obfuscated g()); accessors are
  // obfuscated but toString() is intact and does not consume the one-shot body.
  private fun logHttp() {
    val realCall = load("okhttp3.internal.connection.RealCall")
    val request = findField(realCall) { name == "b" }
    val responseCls = load("okhttp3.Response")
    findMethod(realCall) { name == "g" && parameterTypes.isEmpty() && returnType == responseCls }
        .hookAfter { Log.d("HTTP ${request.get(it.thisObject)} => ${it.result}") }
  }

  // Log every player error (ErrorType + reason + track) -- e.g. why a track advances.
  private fun probePlayerError() {
    val playerError = load("com.spotify.player.model.PlayerError")
    val error = findMethod(playerError) { name == "error" && parameterTypes.isEmpty() }
    val reasons = findMethod(playerError) { name == "reasons" && parameterTypes.isEmpty() }
    val trackUri = findMethod(playerError) { name == "trackUri" && parameterTypes.isEmpty() }
    load("com.spotify.player.model.AutoValue_PlayerError")
        .declaredConstructors
        .first { it.parameterTypes.size == 5 }
        .hookAfter {
          Log.d(
              "PlayerError ${error.invoke(it.thisObject)}" +
                  " reasons=${reasons.invoke(it.thisObject)} track=${trackUri.invoke(it.thisObject)}")
        }
  }

  // Detect Ravelin fraud/anti-tamper activity (its report runs on a deferred worker).
  private fun probeRavelin() {
    val workers =
        listOf(
            "com.ravelin.core.repository.MobileReportWorker",
            "com.ravelin.core.repository.RavelinFingerprintWorker")
    for (worker in workers) feature("ravelin:${worker.substringAfterLast('.')}") {
      findMethod(load(worker)) { name == "e" && parameterTypes.size == 1 }
          .hookBefore { Log.d("Ravelin ${worker.substringAfterLast('.')}.doWork") }
    }
    feature("ravelin:RootCheckerNative") {
      val rootChecker = load("com.ravelin.core.util.security.RootCheckerNative")
      for (m in listOf("b", "checkForRoot")) {
        findMethod(rootChecker) { name == m }
            .hookAfter { Log.d("Ravelin RootCheckerNative.$m -> ${it.result}") }
      }
    }
  }
}
