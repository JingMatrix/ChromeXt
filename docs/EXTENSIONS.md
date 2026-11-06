# WebExtensions support in ChromeXt

Status: **working prototype**, verified on Chrome 156.0.8078.81 / Android 16 (23117RK66C).
Branch: local `master` at `b71fc3e` plus the changes described below.

## What works today

| Area | API | State |
|---|---|---|
| Content scripts | `content_scripts` (js + css, `matches`/`exclude_matches`, `all_frames`) | ✅ injected on every navigation, one private scope per extension |
| Manifest | MV2 `background.page` / `background.scripts`, MV3 `background.service_worker` (run as a page), `default_locale`, `icons`, `permissions` | ✅ |
| Identity | `chrome.runtime.id`, `getManifest()`, `getURL()` | ✅ |
| i18n | `getMessage()` incl. `placeholders`, `getUILanguage()` | ✅ |
| Storage | `storage.local/sync/session` `get/set/remove/clear`, `onChanged` (local) | ✅ persisted under `<browser files>/ExtensionStorage/<id>.json` |
| Messaging | `runtime.sendMessage` (content → background, with response), `runtime.onMessage` | ✅ |
| Ports | `runtime.connect` / `onConnect`, `postMessage`, `onDisconnect` | ✅ bidirectional |
| Tabs | `tabs.query`, `tabs.get`, `tabs.getCurrent`, `tabs.create`, `tabs.update`, `tabs.reload` | ✅ |
| Tabs (continued) | `tabs.sendMessage` (background → content) | ✅ through the content script's own poll loop |
| Misc | `permissions.contains/getAll`, `action.setBadgeText` (stored, no UI), `contextMenus.create` (stored, no UI) | 🟡 accepted, limited |

### Verified with a real extension

**Dark Reader 4.9.134 (MV3, service worker background)** loads, its 3 content scripts run,
its background page answers `cs-bg-document-connect`, and the page really turns dark
(mean luminance of the viewport dropped to 82/255, confirmed visually).

Test extension `test-ext/api-test/` exercises every row above and prints its results into a
floating panel plus a `CXTEST PASS|FAIL [name] detail` line in logcat.

## Architecture

```
user tab (any site)                       host tab (http://127.0.0.1:<port>/host.html)
┌────────────────────────────┐            ┌───────────────────────────────────────────┐
│ init script (ChromeXt)     │            │ host.js                                   │
│  └ content script bundle   │            │  ├ iframe  http://127.0.0.1:<portA>/  ──► ext A background page
│      chrome.* shim         │            │  ├ iframe  http://127.0.0.1:<portB>/  ──► ext B background page
│            │               │            │  └ polls /_api/poll, forwards postMessage │
│            │ dispatch      │            └───────────────────────────────────────────┘
│            ▼               │                          ▲   ▲
│  console.debug channel ────┼──► Kotlin (ExtensionRuntime) ┘   │
└────────────────────────────┘       │  storage, tabs, i18n     │ HostQueue
                                     └──────────────────────────┘
```

* **No WebView.** Instantiating `android.webkit.WebView` inside the browser process aborts
  Chrome: the WebView provider's native library is loaded next to Chromium's own and the
  `JNI_OnLoad` fails (`org.chromium.base.JniAndroid$UncaughtExceptionException`). Verified
  twice; do not reintroduce it.
* **Background pages live in a dedicated tab** (`host.html`) as one iframe per extension,
  each served from a **separate loopback port** so that every extension gets its own origin
  (`localStorage`, ES modules, relative fetches behave like `chrome-extension://`).
* **Kotlin never evaluates into the host tab.** Commands are queued and the host page
  long-polls `/_api/poll` for them. This survives background tabs, where `javascript:`
  navigations are dropped.
* **Content scripts talk over the existing ChromeXt channel**: the shim calls
  `Symbol.<random>.unlock(<key>).dispatch('ext', payload)`, which ChromeXt already routes from
  `addMessageToConsole`; replies come back through `__chromextResolve(uuid, ok, value)`.
* **Pushing into a page uses the page's own channel.** `javascript:` navigations are dropped for
  background tabs, and Chrome 156 removed `WebContents#evaluateJavaScript` (only the internal
  `onEvaluateJavaScriptResult` is left), while the DevTools endpoint serves one client and then
  stops answering. So content scripts poll `runtime.poll` (400 ms visible, 1.5 s hidden) and Kotlin
  hands them whatever was queued for that tab: replies, `tabs.sendMessage` traffic, port messages
  and raw code.
* **Per-extension scope.** The bundle runs in
  `(function(chrome, browser){ … })(api, api)`, so several extensions can coexist on one page
  without touching each other's globals.

## Where extensions live

```
/storage/emulated/0/Android/data/<browser>/files/Extension/<id>/
```

`<id>` is the 32-letter Chrome Web Store id, taken from the CRX3 signed header (or derived from
the manifest `key`), and a slug of the resolved manifest name only as a last resort. Storage of `chrome.storage` lives next to it, in
`/data/data/<browser>/files/ExtensionStorage/<id>.json`.

Open the manager: **page menu (⋮) → Extensions** — the entry is injected on every page (it used to
only appear on ChromeXt's own front-end site, which is why it looked missing). On WebView based
browsers the same entry is added to the selection action mode. The manager is a local page, so its
port changes with the browser process: use the menu entry rather than a bookmark.

Three ways to install:

1. **Manager page** – page menu → *Extensions*, then
   * *从文件安装* for a `.zip` / `.crx`,
   * the text field for a Web Store id or URL (fetched from `clients2.google.com`).
   The page also lists extensions with enable/disable and uninstall.
2. **Directory** – drop an unpacked extension into the path above (owner must be the browser
   uid, e.g. `chown -R 10347:1078 …` for Chrome).
3. Both are picked up on the next browser start; *刷新* in the manager re-scans without restart.

## Known limitations

* `webRequest`, `declarativeNetRequest`, `scripting` (MV3) and `action` popups are not
  implemented; blocking extensions (uBlock Origin) will not work.
* MV3 `service_worker` runs as a normal page: no event-driven wake-up, so an extension that
  relies on being suspended and restarted may misbehave. `importScripts` is polyfilled with
  `document.write`.
* `run_at` is ignored: everything is injected at `document_idle` (ChromeXt injects on
  `onUpdateUrl`).
* Content scripts run in the page's world, not in an isolated world, so a hostile page can
  observe or tamper with them. Same trade-off ChromeXt already makes for UserScripts.
* Delivering into a content script goes through a poll loop (see below), so it costs up to half a
  second of latency and one IPC round trip per poll while a page has extensions.
* Extensions keep a tab open (`host.html`). Closing it stops every background page; the next
  message reopens it.

## Building

The upstream build pins AGP 9.4.1, which does not publish a `linux_arm64` aapt2, so on an
arm64 host (this tablet) the build fails with *aapt2 Daemon startup failed*. `build.gradle.kts`
was bumped to **9.5.0** (the first version shipping `aapt2-…-linux_arm64.jar`); CI on x86_64 is
unaffected.

```sh
export ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
gradle app:assembleDebug          # gradle 9.8.2, no wrapper download needed
```

Deploy:

```sh
pm install -r app/build/outputs/apk/debug/app-debug.apk
am force-stop com.android.chrome   # the module is loaded per process
```

Logs: `logcat -s ChromeXt:*`, the test extension additionally prints `CXTEST …` lines.
