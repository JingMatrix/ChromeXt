# ChromeXt

Add UserScript support to Chrome using Xposed framework

##  How does it work?

We hook a `onUpdateUrl` function in [UserScript.kt](app/src/main/java/org/matrix/chromext/hook/UserScript.kt),
add URL comparison there and evaluate JavaScript using the `javascript:` scheme.

We pay our main efforts to support the latest _stable_ version of Android Chrome
installed from Google Play Store or downloaded APK from the internet.
Please consider update your Android Chrome first before proceeding.

Normally, [Bromite](https://github.com/bromite/bromite) and [Brave](https://github.com/brave/brave-browser) are also supported.

## Usage

Currently, this project requires **Xposed framework** installed.
However, it is possible to make Xposed framework optional.
See related progress in the `Development plans` section.

You can try the following implements of it, depending on your Android version or whether having root enabled:
[LSPosed](https://github.com/LSPosed/LSPosed), [LSPatch](https://github.com/LSPosed/LSPatch),
[EdXposed](https://github.com/ElderDrivers/EdXposed), [TaiChi](https://github.com/taichi-framework/TaiChi),
[VirtualXposed](https://github.com/android-hacker/VirtualXposed), [Dreamland](https://github.com/canyie/Dreamland).

Pick up the latest built APK from my repo's [GitHub Action](https://github.com/JingMatrix/ChromeXt/actions/workflows/android.yml) and install it.
The author uploads releases to [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/org.matrix.chromext/releases) when needed, but not that frequently.

You can then install UserScripts from popular sources: URLs that ends with `.user.js`.
However, this fails for scripts from some domains like `raw.githubusercontent.com`.
For them, please download those scripts using the download button on the top of Chrome's three dot menu, and
then open your downloaded scripts in Chrome. The installation prompt should show up again.
Alternatively, it is possible to use the `Install UserScript` page menu if you simply want to install it
without further editing.


### Supported API

Currently, ChromeXt supports only the following APIs since they are everything the author needs to perform all sort of tasks.

1. @name (colons and backslashes not allowed), @namespace (backslashes not allowed), other similar properties' implements depends on the manage front end
2. @match (must present and conform to the [Chrome Standard](https://developer.chrome.com/docs/extensions/mv2/match_patterns/))
3. @include = @match, @exclude
4. @run-at: document-start, document-end, document-idle (the default and fallback value)
5. @grant GM_addStyle, GM_addElement, GM_xmlhttpRequest, GM_openInTab, GM_registerMenuCommand (`Resources` panel of eruda), GM_unregisterMenuCommand, GM_download, unsafeWindow (= window)
6. @require, @resource (Without [Subresource Integrity](https://www.tampermonkey.net/documentation.php#api:Subresource_Integrity))

These APIs are implemented differently from the official ones, see the source file [LocalScripts.kt](app/src/main/java/org/matrix/chromext/script/LocalScripts.kt) if you have doubts or questions.

### UserScripts manager front end

To manage scripts installed by `ChromeXt`, here are a simple front end hosted on [github.io](https://jingmatrix.github.io/ChromeXt/) and two mirrors of it (in case that you have connection issues): [onrender.com](https://jianyu-ma.onrender.com/ChromeXt/), [netlify.app](https://jianyu-ma.netlify.app/ChromeXt/).

### Edit scripts before installing them

If you cancel the prompt to install a new UserScript, you can then edit it directly in Chrome.
To commit your modifications, long press on some text and follow with a click somewhere, the installation prompt should appear again.

### Limitations

A valid UserScript would fail if the following two conditions hold _at the same time_:

1. The matched website has disabled `script: 'unsafe-eval';` by [Content Security Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP);
2. The script size is nearly 2M.

To deal with this extremely rare case, one should
```
use multiple scripts of normal sizes instead of a giant script
```

## Bonus

### Solution of system gesture conflicts

The forward and backward gestures of Chrome are now available near the vertical center of screen.
On other areas, only the system gesture is available.

### Dev Tools for developers

Tap five times on the Chrome version from the Chrome settings, you will see the `Developer options` menu.
After restarting Chrome, ChromeXt offers you
1. the `Developer tools` page menu for the UserScript manager front end,
2. the `Eruda console` page menu for other pages.

### AD Blocker solution

For blocking network requests, I recommend to use `AdAway` or any proxy AD Blocker such as `clash`.

A content cosmetic blocker is embedded into ChromeXt with the help of eruda.
Open the `Eruda console` from the page menu.
In the `Elements` panel, one can use the `pointer` icon to select elements on the page.
After clicking the `delete` icon for a selected element, a corresponding filter will be saved to the `Resources` panel.
These filters are saved in the browser even after clearing the site's data.

Another way to block ADs is using the [Content-Security-Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/script-src) to block some scripts from loading.
See the official [Content-Security-Policy Blocker](CSP.user.js) UserScript.

### Mocking User Agent

One can edit the user-agent from the `Info` panel of `Eruda console`.
Clicking on the header of `User-Agent` will restore the default user-agent.
The support is still limited, might be improved later.
A valid user-agent should only contain ASCII characters.

## Contribute to this project

Before you submit your pull-requests, please ensure that the command
`./gradlew build` or `gradlew.bat build` produces no warnings and no errors.

Here are corresponding files you might want / need to change:
1. Front end: [manager.vue](https://github.com/JingMatrix/viteblog/tree/master/components/ChromeXt/manager.vue)
2. Tampermonkey API: [LocalScripts.kt](app/src/main/java/org/matrix/chromext/script/LocalScripts.kt)
3. Eruda configuration: [local_eruda.js](app/src/main/assets/local_eruda.js)
4. Support more versions: [proxy](app/src/main/java/org/matrix/chromext/proxy)

## Development plans

- [x] Make it possible to pass intents to Chrome with `file` scheme
- [x] Fix encoding problem for Chrome downloaded Javascript files
- [x] Inject module resource into Chrome
- [x] Implement developer tools
- [x] Use local versions of [eruda](https://github.com/liriliri/eruda)
- [x] Improve eruda incorporation with Chrome
- [x] Add more information in the preference screen
- [x] Support more [Tampermonkey API](https://www.tampermonkey.net/documentation.php)s
- [x] Find elegant way to support Dev Tools for Android 11-
- [x] Improve front end
- [x] Add cosmetic AdBlocker using eruda
- [x] Find way to get current interactive tab
- [x] Remove AndroidX Room dependency to reduce app size
- [x] Support non-split version of Android Chrome
- [x] Solve the menu hook problem for non-split versions
- [x] Handle multiple Tab Model
- [x] Forward Dev Tools server socket
- [x] A mobile friendly Dev Tools front-end
- [x] Allow user to trigger reader mode
- [x] Support @resource API
- [x] Make GestureNav Fix optional
- [x] Add an open source License
- [ ] ~~Support mocking User-Agent~~
- [ ] ~~Support [urlFilter](https://developer.chrome.com/docs/extensions/reference/declarativeNetRequest/#type-RuleCondition) syntax~~
- [x] Improve `Open in Chrome` function
- [x] Implement fully `GM_info`
- [x] Eruda fails due to [Injection Sinks](https://developer.mozilla.org/en-US/docs/Web/API/Trusted_Types_API)
- [ ] Use [Chrome DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/) as UserScript engine
- [ ] Use `adb forward` to support non-root users
- [ ] Turn Xposed into optional dependency
- [ ] Add recommended scripts to the front end manager
