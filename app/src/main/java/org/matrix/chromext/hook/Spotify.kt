package org.matrix.chromext.hook

import de.robv.android.xposed.XC_MethodHook.Unhook
import org.matrix.chromext.utils.*

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun ByteArray.toPrintableString() =
    filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")

object SpotifyHook : BaseHook() {
  var loader = this::class.java.classLoader!!

  data class Page(
      val structure: String,
      val section: String,
      val field: String,
      val toRemove: Set<String>
  )

  val stateOverride =
      mapOf(
          // Disables player and app ads.
          "ads" to false,
          // Works along on-demand, allows playing any song without restriction.
          "player-license" to "premium",
          // Disables shuffle being initially enabled when first playing a playlist.
          "shuffle" to false,
          // Allows playing any song on-demand, without a shuffled order.
          "on-demand" to true,
          // Enable Spotify Connect and disable other premium related UI, like buying premium.
          // It also removes the download button.
          "type" to "premium",
          // Make sure playing songs is not disabled remotely and playlists show up.
          "streaming" to true,
          // Allows adding songs to queue and removes the smart shuffle mode restriction,
          // allowing to pick any of the other modes.
          "pick-and-shuffle" to false,
          // Disables shuffle-mode streaming-rule, which forces songs to be played shuffled.
          "streaming-rules" to "",
          // Enables premium UI in settings and removes the premium button in the nav-bar.
          "nft-disabled" to "1",
          // Enable Cross-Platform Spotify Car Thing.
          "can_use_superbird" to true,
          // Removes the premium button in the nav-bar for tablet users.
          "tablet-free" to false,
      )

  override fun init() {

    // Spoof product state
    // val productStateProto =
    // loader.loadClass("com.spotify.remoteconfig.internal.ProductStateProto")
    // val values_ = findField(productStateProto) { name == "values_" }
    // val accountAttribute = loader.loadClass("com.spotify.remoteconfig.internal.AccountAttribute")
    // val value_ = findField(accountAttribute) { name == "value_" }

    // @Suppress("UNCHECKED_CAST")
    // findMethod(productStateProto) { returnType == productStateProto }
    //     .hookAfter {
    //       val rawData = it.args[0] as ByteArray
    //       Log.d("parsing from raw data ${rawData.toHexString()}")
    //       val state = values_.get(it.result) as Map<String, Any>
    //       for ((key, value) in stateOverride) {
    //         if (state.containsKey(key)) {
    //           val attribute = state.get(key)
    //           Log.d("Overriding ${key}:${value_.get(attribute)} to ${value}")
    //           value_.set(attribute, value)
    //         } else {
    //           Log.d("Key ${key} not found in productState")
    //         }
    //       }
    //       // for ((key, value) in state) {
    //       //   Log.d("${key} [${value_.get(value).javaClass}] ${value_.get(value)}")
    //       // }
    //     }

    val dynamicalStateOverride =
        mapOf<String, String>(
            // "ads" to "0",
            // "player-license" to "premium",
            "shuffle" to "0",
            // "on-demand" to "1",
            "type" to "premium",
            "streaming" to "1",
            // "pick-and-shuffle" to "0",
            // "streaming-rules" to "",
            "nft-disabled" to "1",
            "smart-shuffle" to "AVAILABLE",
        )

    findMethod(LinkedHashMap::class.java) {
          name == "get" && parameterTypes.size == 1 && returnType == Object::class.java
        }
        .hookAfter {
          if (it.args[0] is String &&
              it.result is String &&
              it.thisObject != dynamicalStateOverride) {
            if (dynamicalStateOverride.containsKey(it.args[0])) {
              val newValue = dynamicalStateOverride[it.args[0]]
              Log.d("Overriding ${it.args[0]}:${it.result} -> ${newValue}")
              it.result = newValue
            }
          }
        }

    val nativeStateOverride =
        mapOf<String, String>(
            "ads" to "0",
            "player-license" to "premium",
            "pick-and-shuffle" to "0",
            "on-demand" to "1",
            "streaming-rules" to "",
        )

    val nativeSession = loader.loadClass("com.spotify.connectivity.auth.NativeSession")
    findMethod(nativeSession) { name == "createNativeSessionWithoutAp" }
        .hookBefore {
          val state = it.args[3] as LinkedHashMap<String, String>
          nativeStateOverride.forEach { entry -> state.put(entry.key, entry.value) }
          Log.d("createNativeSessionWithoutAp $state")
        }

    // Enable trackRows view in artist page
    val preparePlayOptions =
        loader.loadClass("com.spotify.player.model.command.options.PreparePlayOptions")
    val option = findMethod(preparePlayOptions) { name == "configurationOverride" }.returnType

    @Suppress("UNCHECKED_CAST")
    findMethod(option) { name == "containsKey" }
        .hookAfter {
          val FIELD = "checkDeviceCapability"
          if (it.args[0] == "signal" && it.thisObject.toString().contains("${FIELD}=")) {
            val queryParameter = it.thisObject as Map<String, Any>
            val store =
                findField(queryParameter::class.java) { type == Array::class.java }
                    .get(queryParameter) as Array<Any>

            val index = store.indexOfLast { it == FIELD || it == "trackRows" }
            store[index] = "trackRows"
            store[index + 1] = true
          }
        }

    // Remove upsell in the context menu of track details
    // We must first find the obfuscated class com.spotify.remoteconfig.runtime.PropertyParser
    var propertyParserFinder: Unhook? = null
    // Find a trampoline by searching smali files ending with `Properties`
    val trampoline =
        loader.loadClass(
            "com.spotify.localfiles.configurationimpl.AndroidLocalFilesConfigurationImplProperties")
    propertyParserFinder =
        findMethod(trampoline) { name == "parse" }
            .hookAfter {
              Log.d("Found PropertyParser at ${it.args[0]}}")
              propertyParserFinder?.unhook()
              findMethod(it.args[0]::class.java) {
                    parameterTypes contentDeepEquals
                        arrayOf(String::class.java, String::class.java, Boolean::class.java) &&
                        returnType == Boolean::class.java
                  }
                  .hookAfter {
                    if (it.args[0] == "android-context-menu" &&
                        it.args[1] == "remove_ads_upsell_enabled") {
                      it.result = false
                    }
                    if (it.args[0] == "android-libs-social-listening" &&
                        it.args[1] == "enable_jam_upsell_in_context_menu_item") {
                      it.result = false
                    }
                    // Log.d("(${it.args[0]}, ${it.args[1]}, ${it.args[2]}) => ${it.result}")
                  }
            }

    // Remove VA restrictions
    val contextJsonAdapter =
        loader.loadClass("com.spotify.voiceassistants.playermodels.ContextJsonAdapter")
    val context = loader.loadClass("com.spotify.player.model.Context")
    val autoValue_Context = loader.loadClass("com.spotify.player.model.AutoValue_Context")

    val preparePlayOptionsJsonAdapter =
        loader.loadClass("com.spotify.voiceassistants.playermodels.PreparePlayOptionsJsonAdapter")
    val autoValue_PreparePlayOptions =
        loader.loadClass("com.spotify.player.model.command.options.AutoValue_PreparePlayOptions")

    val autoValue_PlayerOptionOverrides =
        loader.loadClass("com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides")

    val url = findField(autoValue_Context) { name == "url" }
    val uri = findField(autoValue_Context) { name == "uri" }

    val playerOptionsOverride =
        findField(autoValue_PreparePlayOptions) { name == "playerOptionsOverride" }

    val shufflingContext = findField(autoValue_PlayerOptionOverrides) { name == "shufflingContext" }

    findMethod(contextJsonAdapter) { name == "fromJson" && returnType == context }
        .hookAfter {
          url.set(it.result, (url.get(it.result) as String).replace("station:", ""))
          uri.set(it.result, (uri.get(it.result) as String).replace("station:", ""))
          // Log.d("VA context: ${it.result}", true)
        }

    findMethod(preparePlayOptionsJsonAdapter) {
          name == "fromJson" && returnType == preparePlayOptions
        }
        .hookAfter {
          val player_options_override = playerOptionsOverride.get(it.result)
          var value = findField(player_options_override::class.java) { type == Any::class.java }
          val shuffling_context = shufflingContext.get(value.get(player_options_override))
          value = findField(shuffling_context::class.java) { type == Any::class.java }
          value.set(shuffling_context, false)
          Log.d("VA player options: ${it.result}", true)
        }

    // Remove AD sections in home page and browse page
    val homePage =
        Page(
            structure = "com.spotify.home.evopage.homeapi.proto.HomeStructure",
            section = "com.spotify.home.evopage.homeapi.proto.Section",
            field = "featureTypeCase_",
            toRemove =
                setOf(
                    "IMAGE_BRAND_AD_FIELD_NUMBER",
                    "PREVIEW_FIELD_NUMBER",
                    "VIDEO_BRAND_AD_FIELD_NUMBER",
                ))

    val browsePage =
        Page(
            structure = "com.spotify.browsita.v1.resolved.BrowseStructure",
            section = "com.spotify.browsita.v1.resolved.Section",
            field = "sectionTypeCase_",
            toRemove = setOf("BRAND_ADS_FIELD_NUMBER"))

    for (page in listOf(homePage, browsePage)) {
      val structure = loader.loadClass(page.structure)
      val section = loader.loadClass(page.section)

      val sections_ = findField(structure) { name == "sections_" }
      val featureTypeCase_ = findField(section) { name == page.field }
      // val sectionInfo_ = findField(section) { name == "sectionInfo_" }
      val sectionTypes =
          section.declaredFields
              .filter { it.name.endsWith("_FIELD_NUMBER") }
              .associate { field ->
                field.isAccessible = true
                val fieldValue = field.get(null)
                fieldValue to field.name
              }

      findMethod(structure) { returnType == sections_.type }
          .hookBefore {
            @Suppress("UNCHECKED_CAST")
            val sections = sections_.get(it.thisObject) as MutableList<Any>

            // See source code of ProtobufArrayList at
            // https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/ProtobufArrayList.java
            findField(sections::class.java, true) { type == Boolean::class.java }
                .set(sections, true)
            // Set sections mutable
            sections.removeIf { page.toRemove.contains(sectionTypes[featureTypeCase_.get(it)]) }
          }
    }

    // Remove premium upsell
    val fetchMessageRequest =
        loader.loadClass(
            "com.spotify.messaging.clientmessagingplatform.clientmessagingplatformsdk.data.models.network.FetchMessageRequest")
    val entityUri = findField(fetchMessageRequest) { name == "entityUri" }
    val purchaseAllowed = findField(fetchMessageRequest) { name == "purchaseAllowed" }

    findMethod(fetchMessageRequest) { name == "getOpportunityId" }
        .hookAfter {
          val request = it.thisObject
          if ((entityUri.get(request) as String).startsWith("upsell")) {
            purchaseAllowed.set(request, false)
          }
        }

    // Intercept possible productState inconsistency detection mechanism
    var must_logout = false
    val sessionState = loader.loadClass("com.spotify.connectivity.sessionstate.SessionState")
    findMethod(sessionState) { name == "getLogoutReason" }
        .hookAfter { Log.d("Logout with reason ${it.result}") }

    val response = loader.loadClass("com.spotify.cosmos.cosmos.Response")
    val status_response = findField(response) { name == "status" }
    val uri_response = findField(response) { name == "uri" }
    val body_response = findField(response) { name == "body" }

    val resolverCallbackReceiver =
        loader.loadClass("com.spotify.cosmos.routercallback.ResolverCallbackReceiver")
    findMethod(resolverCallbackReceiver) { name == "sendOnResolved" }
        .hookBefore {
          val res = it.args[0]
          val status_ = status_response.get(res) as Int
          val uri_ = uri_response.get(res) as String
          val body_ = body_response.get(res) as ByteArray
          val bodyString = body_.toPrintableString()
          if (uri_.endsWith("SubValues") || uri_.endsWith("addOnPushedMessageForIdentFilter")) {
            Log.d("blocked [uri, body]: [$uri_, $bodyString]")
            it.result = true
          }
        }

    val clientBase = loader.loadClass("com.spotify.esperanto.esperanto.ClientBase")
    val coroutineClientBase =
        loader.loadClass("com.spotify.esperanto.esperanto.CoroutineClientBase")
    val empty = loader.loadClass("com.google.protobuf.Empty")
    val single = loader.loadClass("io.reactivex.rxjava3.core.Single")
    val exceptionHelper = loader.loadClass("io.reactivex.rxjava3.internal.util.ExceptionHelper")

    val error_single =
        findMethod(single) {
          name == "error" && parameterTypes.size == 1 && parameterTypes[0] == Throwable::class.java
        }
    val never_single = findMethod(single) { name == "never" && parameterTypes.size == 0 }
    val type_protobuf = findMethod(empty) { name == "getDefaultInstanceForType" }.returnType
    val toByteArray = findMethod(type_protobuf) { name == "toByteArray" }
    val toByteString = findMethod(type_protobuf) { name == "toByteString" }

    findMethod(clientBase) { name == "callSingle" }
        .hookBefore {
          val service = it.args[0] as String
          val method = it.args[1] as String
          val payload = toByteArray.invoke(it.args[2]) as ByteArray
          val payloadString = payload.toPrintableString()
          if (service.startsWith("spotify.ads") || method == "writeProductStateToLegacyStorage") {
            Log.d("blocked callSingle: $service $method $payloadString")
            it.result = error_single.invoke(null, Exception("Blocked via Xposed"))
          }
          // if (service.contains("connectivity.auth") || method.contains("Token")) {}
          // if (method == "removeUser" && !must_logout) it.result = never_single.invoke(null)
        }

    isInit = true
  }
}
