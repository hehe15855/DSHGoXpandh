import fs from 'node:fs'

export const name = 'local-xhook'

/**
 * 控制目录：依次探测几个可能的位置，哪个里面有模块导出的文件就用哪个。
 * 这样别人把模块装到别的路径、或者用了旧版本的目录名，插件也能找到。
 */
function findCtrl() {
  const cands = [
    '/sdcard/DSH-Output/xhook',
    '/sdcard/xhook',
    '/sdcard/Download/xhook',
    '/storage/emulated/0/DSH-Output/xhook',
  ]
  for (const c of cands) {
    try {
      if (fs.existsSync(c + '/capabilities.json') || fs.existsSync(c + '/status.json')) return c
    } catch {}
  }
  return cands[0]
}
const CTRL = findCtrl()
const OFF = CTRL + '/off'
const STATUS = CTRL + '/status.json'
/** 用户放配方/配置的目录：优先 Download，其次控制目录里的 recipes */
function findUser() {
  const cands = ['/sdcard/Download/xhook', CTRL + '/recipes', '/sdcard/xhook']
  for (const c of cands) {
    try { if (fs.existsSync(c)) return c } catch {}
  }
  return cands[0]
}
const USER = findUser()
const SCRIPTS = CTRL + '/scripts'
const ABIL = CTRL + '/abilities'
/** 能力列表以模块导出的清单为准；读不到才用内置这份兜底 */
function abilities() {
  try {
    const cap = JSON.parse(fs.readFileSync(CTRL + '/capabilities.json', 'utf8'))
    const ms = cap.modes || []
    if (ms.length) return ms.map((m) => [m.id, m.name || m.id])
  } catch {}
  return [
    ['log', '明文日志'], ['args', '改入参'], ['return', '改返回值'],
    ['js', '注入 JS'], ['cookie', '读 Cookie'], ['call', '调静态方法'],
  ]
}

function readStatus() { try { return JSON.parse(fs.readFileSync(STATUS, 'utf8')) } catch { return null } }
function exists(p) { try { return fs.existsSync(p) } catch { return false } }
function listDir(d) { try { return fs.readdirSync(d) } catch { return [] } }

function scripts() {
  return listDir(SCRIPTS).filter((n) => n.endsWith('.js')).map((n) => ({ name: n, on: !exists(SCRIPTS + '/' + n + '.off') }))
}
function abilOn(key) { return !exists(ABIL + '/' + key + '.off') }

function toggleInject() {
  try {
    if (exists(OFF)) { fs.unlinkSync(OFF); return { on: true } }
    fs.mkdirSync(CTRL, { recursive: true })
    fs.writeFileSync(OFF, '')
    return { on: false }
  } catch { return null }
}

function setAllScripts(on) {
  try {
    for (const s of scripts()) {
      const f = SCRIPTS + '/' + s.name + '.off'
      if (on) { if (exists(f)) fs.unlinkSync(f) }
      else if (!exists(f)) fs.writeFileSync(f, '')
    }
    return true
  } catch { return false }
}

export function apply(ctx) {
  ctx.inject(['dshGo'], (ctx) => {
    ctx.dshGo.strip('xhook-strip', () => {
      const s = readStatus()
      if (s === null) return { text: 'xhook 未安装' }
      return { text: 'xhook ' + s.version + ' ' + (s.inject ? '开' : '关') }
    })

    ctx.dshGo.panel('xhook-panel', {
      view: () => {
        const s = readStatus()
        const children = []
        if (s === null) {
          children.push({ type: 'text', text: '没读到 status.json：模块没装，或还没打开过它的设置页。' })
        } else {
          children.push({ type: 'metric', label: '版本', value: s.version })
          children.push({ type: 'metric', label: '通道', value: s.inject ? '开' : '关' })
        }
        const ab = abilities().map(([k, label]) => label + (abilOn(k) ? '✓' : '✗')).join('  ')
        children.push({ type: 'text', text: '能力：' + ab })
        const sc = scripts()
        children.push({ type: 'text', text: '脚本：' + (sc.length === 0 ? '（无）' : sc.map((x) => x.name + (x.on ? '✓' : '✗')).join('  ')) })
        const cfgs = listDir(USER)
        children.push({ type: 'text', text: '配置：' + (cfgs.length === 0 ? '（无）' : cfgs.join('、')) })
        children.push({ type: 'actions', items: [
          { label: s && s.inject ? '关通道' : '开通道', action: 'toggle' },
          { label: '脚本全开', action: 'scriptsOn' },
          { label: '脚本全关', action: 'scriptsOff' },
        ] })
        return { type: 'stack', children }
      },
      actions: {
        toggle: () => {
          const r = toggleInject()
          return { toast: r === null ? '改不了，检查存储权限' : (r.on ? '通道已开' : '通道已关') }
        },
        scriptsOn: () => ({ toast: setAllScripts(true) ? '脚本全开' : '改不了' }),
        scriptsOff: () => ({ toast: setAllScripts(false) ? '脚本全关' : '改不了' }),
      },
    })
  })
}
