const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('native', {
  read: (name) => ipcRenderer.invoke('store:read', name),
  write: (name, text) => ipcRenderer.invoke('store:write', name, text),
  age: (name) => ipcRenderer.invoke('store:age', name),
  clear: (prefix) => ipcRenderer.invoke('store:clear', prefix),
  json: (url) => ipcRenderer.invoke('api:json', url),
  xtList: (base, user, pass, kind) => ipcRenderer.invoke('xt:list', base, user, pass, kind),
  m3u: (url) => ipcRenderer.invoke('m3u:load', url),
  fullscreen: (on) => ipcRenderer.invoke('win:fullscreen', on),
  isFullscreen: () => ipcRenderer.invoke('win:isFullscreen'),
  onFullscreen: (cb) => ipcRenderer.on('fs', (_e, v) => cb(v)),
  version: () => ipcRenderer.invoke('app:version'),
  openExternal: (u) => ipcRenderer.invoke('app:openExternal', u),
  quit: () => ipcRenderer.invoke('app:quit')
});
