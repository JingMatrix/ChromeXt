# ChromeXt

Add UserScript and DevTools supports to Chromium based and WebView based browsers using Xposed framework.

##  How does it work?

We hook the `onUpdateUrl` function in [UserScript.kt](app/src/main/java/org/matrix/chromext/hook/UserScript.kt),
add URL comparison there and evaluate JavaScript using the `javascript:` scheme (or DevTools Protocol when possible).

Chromium based browsers,
such as [Egde](https://www.microsoft.com/en-us/edge/download),
[Bromite](https://github.com/bromite/bromite),
[Samsung Internet](https://en.wikipedia.org/wiki/Samsung_Internet),
and [Brave](https://github.com/brave/brave-browser), are fully supported.

Most WebView based browsers are also supported, if not, please report it.
Note for WebView based browsers users: you _only_ need to enable this module for the browser application you wish to use, _not_ for any possible WebView applications, _neither_ for the Android system.

## Usage

<p align="center"><a href="https://www.youtube.com/watch?v=1Qm4dU-XnJM"><img src="https://img.youtube.com/vi/1Qm4dU-XnJM/0.jpg" /></a></p>

ChromeXt requires **Xposed framework**.

For root users, install [LSPosed](https://github.com/LSPosed/LSPosed) first,
pick up the latest built APK from my repo's [GitHub Actions](https://github.com/JingMatrix/ChromeXt/actions) and install it.

For non-root users,
I modify a bit [LSPatch](https://github.com/JingMatrix/LSPatch) to support `ChromeXt`; here is how to use it:
1. Download the latest `lspatch-release` from my [GitHub Actions](https://github.com/JingMatrix/LSPatch/actions).
2. Download the latest `ChromeXt.apk` from my [GitHub Actions](https://github.com/JingMatrix/ChromeXt/actions).
3. Extract previously downloaded files to get files `lspatch.jar` (with some suffix) and `ChromeXt-signed.apk`.
4. Patch your APK (taking `arm64_ChromePublic.apk` as example) using the following command: `java -jar lspatch.jar arm64_ChromePublic.apk -d -v -m ChromeXt-signed.apk --force`. If `java` environment is not available, please consider using the provided `manager` APK.
5. Install the patched APK, which might require you to first uninstall the one on your devices.

Notes: currently _to download_ files from `GitHub Actions`, one needs to log in GitHub.

The author uploads releases to [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/org.matrix.chromext/releases) when needed, but not that frequently.

You can then install UserScripts from popular sources: URLs that ends with `.user.js`.

### Supported API

Currently, ChromeXt supports almost all [Tampermonkey APIs](https://www.tampermonkey.net/documentation.php?locale=en):

1. @name (colons not allowed), @namespace, @description and so on
2. @match (conform to the [Chrome Standard](https://developer.chrome.com/docs/extensions/mv2/match_patterns/), supports [regular expressions](https://developer.android.com/reference/java/util/regex/Pattern))
3. @include = @match, @exclude
4. @run-at: document-start, document-end, document-idle (the default and fallback value)
5. @grant: GM_addStyle, GM_addElement, GM_xmlhttpRequest, GM_openInTab, GM_registerMenuCommand (shown in the `Resources` panel of eruda), GM_unregisterMenuCommand, GM_download, unsafeWindow (= window)
6. @grant: GM_setValue, GM_getValue (less powerful than GM.getValue), GM_listValues, GM_addValueChangeListener, GM_removeValueChangeListener, GM_setClipboard, GM_cookie, GM_notification
7. @require, @resource (without [Subresource Integrity](https://www.tampermonkey.net/documentation.php#api:Subresource_Integrity))

These APIs are implemented differently from the official ones, please refer to the source files
[Local.kt](app/src/main/java/org/matrix/chromext/script/Local.kt) and
[GM.js](app/src/main/assets/GM.js) if you have doubts or questions.

Moreover, there is the powerful (also dangerous) `GM.ChromeXt` API, which must be declared by `@grant GM.ChromeXt` to _unlock_ its usage.
It is locked by default so that the users are protected from malicious UserScripts exploiting ChromeXt.
This API allows scripts to use the JavaScript method `ChromeXt.dispatch(action, payload)`, which is fundamental to implement other APIs. (Hence, one can find usage examples in [GM.js](app/src/main/assets/GM.js)).
Dispatched `action` and `payload` are handled by [Listener.kt](app/src/main/java/org/matrix/chromext/Listener.kt).

### UserScripts manager front end

To manage scripts installed by `ChromeXt`, here are a simple front end hosted on [github.io](https://jingmatrix.github.io/ChromeXt/) and two mirrors of it (in case that you have connection issues): [onrender.com](https://jianyu-ma.onrender.com/ChromeXt/), [netlify.app](https://jianyu-ma.netlify.app/ChromeXt/).

### Edit scripts before installing them

If you cancel the prompt of installing a new UserScript, then you can edit it directly in Chrome.
Use the `Install UserScript` page menu to install your modified UserScript.

### DevTools for developers

From the three dots page menu, ChromeXt offers you the
1. `Developer tools` menu for the UserScript manager front end,
2. `Eruda console` menu for other pages.

For `Edge` browser, these menus are moved to the page info menu,
which locates at the left corner inside the URL input bar.

For WebView based browsers and _Samsung Internet_, these menu items are presented in the context menu.

## Bonus

Since WebView based browsers have no unified designs, the following
first four features are not supported for them.
(By the same reason, they are neither supported for _Samsung Internet_.)

### Open in Chrome

The application `ChromeXt` is able to
1. received shared texts to search them using `Google`,
2. open JavaScript files to install them as UserScripts.

The reversed priority order of opening which Chromium based browsers is given in [AndroidManifest.xml](app/src/main/AndroidManifest.xml).

### Solution of system gesture conflicts

By default, the history forward gesture of Chrome is available near the vertical center of screen.
On other areas, only the system gesture is available.
One can disable this behavior through the `Developer options` menu.
(Tap seven times on the Chrome version from the Chrome settings, you will see the `Developer options` menu.)
(In [Vivaldi](https://vivaldi.com/en/android/) browsers, `Developer options` menu is removed by its developers.)

### Enable reader mode manually

ChromeXt adds a book icon in the page menu to enable reader (distiller) mode manually.

### Export browser bookmarks

Bookmarks can be exported in HTML format through the `Developer options` menu.

### AD Blocker solution

For blocking network requests, I recommend to use `AdAway` or any proxy AD Blocker such as `clash`.

A content cosmetic blocker is embedded into ChromeXt with the help of eruda.
To use it, first open the `Eruda console`.
In the `Elements` panel, one can use the `pointer` icon to select elements on the page.
After clicking the `delete` icon for a selected element, a corresponding filter will be saved to the `Resources` panel,
where one can manage previous added filters.
These filters are saved in the browser even after clearing the site's data.

Another way to block ADs is using the [Content-Security-Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/script-src) to block some scripts from loading.

### User-Agent spoofing

One can edit the User-Agent from the `Info` panel of `Eruda console`.
A valid User-Agent should contain only ASCII characters.
Currently, ChromeXt only changes the `User-Agent` HTTP header, which
works well but is [deprecated](https://wicg.github.io/ua-client-hints/#user-agent).

For Chromium based browsers, when the User-Agent spoofing is not taking effects, refresh the page using the reload button in the page menu.
(By contrast, a swipe refresh might be insufficient.)

Note that the DevTools can also change User-Agent.

## Contribute to this project

Before you submit your pull-requests, please ensure that the command
`./gradlew build` or `gradlew.bat build` produces no warnings and no errors.

Here are corresponding files you might want / need to change:
1. Front end: [manager.vue](https://github.com/JingMatrix/jingmatrix.github.io/tree/main/components/ChromeXt/manager.vue)
2. Tampermonkey API: [Local.kt](app/src/main/java/org/matrix/chromext/script/Local.kt)
and [GM.js](app/src/main/assets/GM.js)
3. Eruda configuration: [eruda.js](app/src/main/assets/eruda.js)
4. Support more WebView based browsers: [WebView.kt](app/src/main/java/org/matrix/chromext/hook/WebView.kt)

## Development plans

- [x] Make it possible to pass intents to Chrome with `file` scheme
- [x] Fix encoding problem for Chrome downloaded JavaScript files
- [x] Inject module resource into Chrome
- [x] Implement developer tools
- [x] Use local versions of [eruda](https://github.com/liriliri/eruda)
- [x] Improve eruda incorporation with Chrome
- [x] Add more information in the preference screen
- [x] Support more [Tampermonkey API](https://www.tampermonkey.net/documentation.php)s
- [x] Find elegant way to support DevTools for Android 11-
- [x] Add cosmetic AdBlocker using eruda
- [x] Find way to get current interactive tab
- [x] Remove AndroidX Room dependency to reduce app size
- [x] Support non-split version of Android Chrome
- [x] Solve the menu hook problem for non-split versions
- [x] Handle multiple Tab Model
- [x] Forward DevTools server socket
- [x] A mobile friendly DevTools front end
- [x] Allow user to trigger reader mode
- [x] Support @resource API
- [x] Make GestureNav Fix optional
- [x] Add an open source License
- [x] Support mocking User-Agent
- [ ] ~~Support [urlFilter](https://developer.chrome.com/docs/extensions/reference/declarativeNetRequest/#type-RuleCondition) syntax~~
- [x] Improve `Open in Chrome` function
- [x] Implement fully `GM_info`
- [x] Eruda fails due to [Injection Sinks](https://developer.mozilla.org/en-US/docs/Web/API/Trusted_Types_API)
- [x] Hide page_info panel automatically
- [x] Fix page menu injection position
- [ ] ~~Use [Chrome DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/) as UserScript engine~~
- [ ] ~~Use `adb forward` to support non-root users~~
- [x] Fully support WebView based browsers
- [x] Fix [LSPatch for isolated process](https://github.com/LSPosed/LSPatch/issues/190) issue
- [x] Implement UserScript storage
- [x] Re-implement GM_xmlhttpRequest
- [x] Convert exported bookmarks to HTML format
- [x] Show executed scripts on current page
- [x] Make a YouTube presentation video
- [x] Support Samsung Internet browser
- [x] Implement GM_cookie
- [x] Improve valid UserScripts Url detection
- [ ] Save and present script errors and `GM_log` logs
- [ ] Use `iframe` and local server to run general [WebExtensions](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions)
- [ ] Bypass `style-src` rule for `eruda`, such as Mastodon
- [ ] Support importing UserScripts from Tampermonkey exports
- [ ] Support backup and restore
- [ ] Add recommended UserScripts to the front end manager
- [x] Add [chrome devtools front-end](https://chromium.googlesource.com/devtools/devtools-frontend/) for Edge, see [devtools_http_handler.cc](https://source.chromium.org/chromium/chromium/src/+/main:content/browser/devtools/devtools_http_handler.cc) as reference.
- [x] Hide inserted menu for non-page
