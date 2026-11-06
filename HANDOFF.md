# ChromeXt 扩展支持 · 交接文档

> 最后更新：2026-11-06 14:40 ｜ 状态：**三项功能全部实现并实测，PR 已开，等维护者回复**
> 代码：`/storage/emulated/0/Tools/LSPilot/projects/chromext`（分支 `extension-support` @ `fe65f4b`，工作树干净）

## 一句话

WebExtensions 支持从骨架做成了能用：内容脚本注入、后台页（MV3 会空闲卸载/按需拉起）、`chrome.*` 子集、
请求过滤（主框架）、扩展菜单行与 popup、本地管理页。自测扩展 **12/12**，真实扩展 Dark Reader / Tampermonkey 能跑。
PR **#319**（OPEN / MERGEABLE / 45 文件 +5149 −160）。

## 你要做的实际测试：按这个顺序

### 0. 前置（都已经就绪，确认一下即可）

| 项 | 期望值 |
|---|---|
| 模块 | `org.matrix.chromext` 3.9.1 debug，`lastUpdateTime=2026-11-06 14:19:55` |
| 设备上装的扩展 | `api-test`、`popup-test`、`sw-test`、`eimadpbcbfnmbkopoojfekhnkhdbieeh`(Dark Reader)、`msg-extname`(Tampermonkey) |
| 扩展目录 | `/storage/emulated/0/Android/data/com.android.chrome/files/Extension/` |
| 扩展存储 | `/data/data/com.android.chrome/files/ExtensionStorage/*.json` |

### 1. 管理页（装 / 卸 / 启停）

Chrome 任意页面 → 右上 **⋮ → Extensions** → 打开 `http://127.0.0.1:<端口>/manager.html`

可验证：列表里 5 个扩展、图标、版本、开关、卸载；底部「扩展目录」后面会显示**宿主进程状态**（运行中/启动中/未启动）。

- 从文件装：点虚线框选 `.zip` / `.crx`
- 从商店装：输入 32 位扩展 ID（如 Dark Reader `eimadpbcbfnmbkopoojfekhnkhdbieeh`）→ 安装
- ⚠ 管理页端口每个 Chrome 进程都变，**别存书签**，每次从菜单进

### 2. 内容脚本 + `chrome.*`（自测扩展）

打开**任意网站** → 页面不再有浮层（已按你的要求收窄），结果在 logcat：

```sh
logcat -d | grep -E "CXTEST (PASS|FAIL)" | tail -12
```

期望 12 行全 PASS：`runtime.id` / `getManifest` / `getURL` / `i18n`×2 / `storage` / `sendMessage→后台` /
`后台探针` / `port 双向通信` / `tabs.query` / `cosmetic 隐藏` / `后台→内容脚本`。

想看**可视面板**：打开扩展自己的自检页（端口从 `http://127.0.0.1:15243/host.html` 里能查到 api-test 的 port，
或直接看 logcat 里 `Extension api-test served at http://127.0.0.1:<p>/`）：
`am start -a android.intent.action.VIEW -d "http://127.0.0.1:<p>/test.html"`

### 3. 请求过滤（主框架）

```
am start -a android.intent.action.VIEW -d "https://example.com/blocked"           # 期望被拦：api-test blocks …
am start -a android.intent.action.VIEW -d "https://example.com/blocked-allowed"   # 期望放行（高优先级 allow）
am start -a android.intent.action.VIEW -d "https://example.com/redirect-me"       # 期望跳到 https://example.com/
am start -a android.intent.action.VIEW -d "https://example.com/wr-block"          # 期望 webRequest cancel
```
对应日志：`blocks ` / `redirects ` / `A webRequest listener cancelled`。
⚠ **每个 Chrome 会话的第一次导航只受 DNR 约束**（webRequest 监听器要等后台页加载完才注册）。

### 4. 菜单与 popup

⋮ 菜单里应当出现（在 ChromeXt 内置项附近）：

| 行 | 来源 | 点击效果 |
|---|---|---|
| ChromeXt 测试菜单项 | api-test 的 `contextMenus.create` | logcat：`CXBG contextMenus.onClicked {"menuItemId":"cx-test-item",…}` |
| ChromeXt API Test | api-test 的 `browser_action`（无 popup） | logcat：`CXBG action.onClicked tab=…` |
| ChromeXt Popup Test | popup-test（有 popup） | 新标签页打开 popup，页面显示 `id / manifest / storage / tabs` |
| Dark Reader | 有 popup | 新标签页打开它的 popup |
| Tampermonkey | 有 popup | 新标签页打开它的 popup |

popup 页的自检日志：`CXPOPUP id=popup-test | manifest=… | storage={"popupRan":true} | tabs=N`

### 5. MV3 service worker 生命周期

```sh
# 打开任意页后静置 40 秒，再访问一次
logcat -d | grep -E "host: (suspend|wake)|CXSW boot"
```
期望：`host: suspend sw-test`（空闲 30s 后 iframe 被移除）→ 下次来消息 `host: wake sw-test` + 新的 `CXSW boot`
+ 被唤醒的事件被重放（内容脚本拿到 `CXSW content hello {…}`）。
⚠ 宿主进程被 Android 冻结时（`ps -A | grep chromext` 显示 `do_freezer_trap`）扫描会停 —— 冻结的宿主本来也没跑东西，行为一致；投递会 poke 唤醒。

## 怎么继续干活（环境已就绪）

```sh
cd /storage/emulated/0/Tools/LSPilot/projects/chromext
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk \
       JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64

gradle format app:assembleDebug            # 用系统 gradle 9.8.2；别用 ./gradlew（要下 9.8.1）
cp app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/ws/out/ChromeXt-debug.apk
pm install -r /data/local/tmp/ws/out/ChromeXt-debug.apk
am force-stop com.android.chrome && am start -a android.intent.action.VIEW -d "https://example.com"

logcat -d | grep -E "ChromeXt:" | tail -30      # Debug 构建才有 Log.d
```

改测试扩展（`test-ext/*`）后要同步到设备并修属主：

```sh
BASE=/storage/emulated/0/Android/data/com.android.chrome/files/Extension
cp -r test-ext/api-test/. $BASE/api-test/ && chown -R 10347:1078 $BASE
```

调试小抄：沙箱（`run_command`）与 Chrome 共享网络命名空间，可直接
`urllib.request.urlopen("http://127.0.0.1:15243/host.html")` 看宿主页/扩展端口，甚至和
`@chrome_devtools_remote` 通话（当初就是靠它定位 DevTools 只能单 client）。

## 代码地图

```
extension/
  Manifest.kt    match pattern、content_scripts、dnr ruleset 列表、i18n、isServiceWorker
  Extensions.kt  注册表：扫描/启停/storage/zip·crx 安装/商店下载/CRX3 id 提取/B锁定 forget
  Server.kt      loopback：每扩展一个端口 + 管理页 + /_api/{poll,hostmsg,state,install,…} + HostQueue
  ChromeApi.kt   生成注入 JS：cfg + 共用 shim + content bundle / 后台页脚本 / shimOnly
  Runtime.kt     动作分发：storage/i18n/tabs/ports/消息/webRequest/dnr/cosmetic/菜单与 popup 存取
  Outbox.kt      每 (扩展, tab) 的命令队列，内容脚本轮询来取
  Blocking.kt    DNR 规则解析与匹配、webRequest 注册与主框架取消/跳转
  Menus.kt       扩展菜单行 → ChromeXt 页面菜单，点击回传
  HostService.kt ChromeXt 自己进程里的 WebView 宿主（android:process=":host"）
  HostClient.kt  浏览器侧绑定 + poke（解冻）
assets/  chrome-api.js（chrome.* 兼容层，每扩展一个轮询/注册实例）
         manager.*（本地管理页）、host.html/host.js（宿主页，iframe + 长轮询 + SW 生命周期）
接线：Listener.kt（ext 动作 + 打开管理页）、script/Manager.kt（每次导航注入 content/扩展页 shim）、
      hook/PageMenu.kt（内置项 + 扩展行）、hook/UserScript.kt（导航前过 Blocking）、Chrome.kt（tabList 走 tab model）
```

## 五条硬结论（改动前必读，都是实测踩出来的）

1. **浏览器进程里不能 `new WebView`** —— 与 Chromium 原生库冲突、`JNI_OnLoad` 失败、**Chrome 整个崩**。后台页只能放 `:host` 进程。
2. **DevTools 端点只能服务一个 client** —— 之后 `/json` 超时、`/devtools/page/<id>` 返 500 约 40 秒；且 Chrome 156 删了
   `WebContents#evaluateJavaScript`。⇒ 页面方向只能让内容脚本自己轮询（`runtime.poll`）。
3. **子资源没有 Java hook 点** —— `base.apk`/`split_chrome.apk` 里没有 Cronet、没有 `UrlRequest`，页面资源在 C++ 网络服务里。
   ⇒ 请求过滤只做主框架，元素隐藏走 cosmetic。
4. **轮询循环 / webRequest 注册表必须每扩展一份**（各自闭包 + `globalThis.__chromext*[id]`）；写成页面级单例会让最后加载的扩展抢走所有人的命令。
5. **ChromeXt 每次 URL update 会重复注入**，第二份 bundle 会顶掉第一份的监听器 ⇒ `__chromextInjected[extId]` 去重。

补充：`ChromeTabbedActivity.getDeclaredMethods()` 在 156 会抛 `NoClassDefFoundError`，tab model 只能从**字段**（selector）取；
懒恢复的标签页 `loadIfNeeded`+`loadUrl` 静默无效，`setIndex` 与 `moveTab` 混淆同签名（别盲试，会误伤用户标签页）。

## 已知短板 / 下一步

| 优先级 | 事项 |
|---|---|
| P0 | 子资源拦截 / 改头（**无解**，除非 hook native；文档已写明） |
| P1 | `action` 真正画 badge（现在 `setBadgeText` 只存）、`contextMenus` 子菜单/radio |
| P2 | DNR 的 session rules / `modifyHeaders`；`scripting.registerContentScripts` |
| P3 | `run_at: document_start`（现在统一 document_idle）、isolated world |

## PR 相关（重要）

- PR：**https://github.com/JingMatrix/ChromeXt/pull/319**（OPEN，MERGEABLE，45 文件）
- head：`RONUKUIGO:extension-support` @ `fe65f4b`；base `JingMatrix:master` @ `b71fc3e`
- **fork 不能删**：头仓库没了 PR 会立即关闭。合并或放弃后再说（见 MANIFEST §68.11 的取舍）
- `gh pr edit` 在这个仓库**必挂**（GraphQL `Projects (classic) is being deprecated`）→ 用 REST：
  `gh api -X PATCH /repos/JingMatrix/ChromeXt/pulls/319 -F body=@file` / `-f title="…"`
- CI 仍 `action_required`（首次贡献者需维护者点 Approve and run），**不是失败**

## 收尾状态

- 工作树干净（`git status --short` = 0），ktfmt 已跑过，`gradle app:assembleDebug` 通过
- 成品 APK `outputs/apk/ChromeXt-v39-extsupport.apk`，sha256 `0a503c5e…`（设备上就是它）
- 唯一未清项：`/data/local/tmp/cb-backup-20261104-2200`（11-04 的历史备份，不是这次的，可删）
- 设备上 5 个扩展随时可在管理页启停/卸载，测试完不用清理
