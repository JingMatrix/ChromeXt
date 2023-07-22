const inFrame = typeof globalThis.ChromeXt == "undefined";

class Runtime {
  constructor(manifest) {
    this.manifest = manifest;
  }
  getManifest() {
    return this.manifest;
  }
}

window.chrome = {
  accessibilityFeatures: {},
  action: {},
  alarms: {},
  browser: {},
  browserAction: {},
  browsingData: {},
  bookmarks: {},
  commandsw: {},
  contentSettings: {},
  contextMenus: {},
  cookies: {},
  declarativeContent: {},
  declarativeWebRequest: {},
  devtools: {},
  documentScan: {},
  downloads: {},
  enterprise: {},
  events: {},
  extension: {},
  fileBrowserHandler: {},
  fontSettings: {},
  gcm: {},
  history: {},
  i18n: {},
  identity: {},
  idle: {},
  input: {},
  loginState: {},
  management: {},
  networking: {},
  notifications: {},
  offscreen: {},
  omnibox: {},
  pageAction: {},
  pageCapture: {},
  permissions: {},
  platformKeys: {},
  power: {},
  printerProvider: {},
  privacy: {},
  proxy: {},
  runtime: new Runtime(extension),
  search: {},
  serial: {},
  scripting: {},
  scriptBadge: {},
  sessions: {},
  sidePanel: {},
  storage: {},
  socket: {},
  system: {},
  tabCapture: {},
  tabs: {},
  tabGroups: {},
  topSites: {},
  tts: {},
  ttsEngine: {},
  types: {},
  vpnProvider: {},
  wallpaper: {},
  webNavigation: {},
  webRequest: {},
  webstore: {},
  windows: {},
};