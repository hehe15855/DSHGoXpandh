// local-dshxpandh —— DSH 与 DSHxpandh(LSPosed 模块) 的唯一对接层。
//
// 一个包同时提供两套界面：
//   · 给人看：App 输入框上方的状态条 + 面板（strip / panel）
//   · 给 agent 用：dshxpandh_status / dshxpandh_script / dshxpandh_inject / dshxpandh_logs 四个工具
// 控制层内联，不再依赖 scripts/dshxpandh-inject.sh 之类外部脚本。
//
// 模块侧协议（读 HookEntry.java 得出）：
//   命令通道  /data/user/0/<pkg>/files/dshxpandh.cmd.json = {ts, js, match, value}，需 root 写
//   脚本层    <ROOT>/scripts/*.js 每 3 秒自动注入所有已登记 WebView（含腾讯 XWeb）
//   开关      <ROOT>/off 停全部注入；<ROOT>/stealth 隐身
//   日志      只走 XposedBridge.log，从 /data/adb/lspd/log/modules*.log 读回
import { execFile } from 'node:child_process'
import fs from 'node:fs'
import crypto from 'node:crypto'

export const name = 'local-dshxpandh'
// 注册工具必须先声明 tools：没有 inject 时 cordis 不给 ctx.tools（诊断里那句
// 「cannot get property "tools" without inject」就是这个），四个工具会全部注册失败。
// 别把 dshGo 写进来 —— 它不是 cordis 服务（没有任何插件声明它），写错会让 apply 永不执行。
export const inject = ['tools', 'systemPrompt']

const PKG = 'io.github.hehe15855.dshxpandh'
const ACTIVITY = PKG + '/.SettingsActivity'
// 远端仓库真名是 DSHGoXpandh（DSHxpandh 那个是 404），别跟显示名一起改
const RELEASE = 'https://github.com/hehe15855/DSHGoXpandh/releases/latest'

// 控制目录：统一为 /sdcard/DSHxpandh（单一来源，DSH-Output 旧候选已废弃）
function pickRoot() {
  const cands = ['/sdcard/DSHxpandh', '/sdcard/Download/DSHxpandh']
  for (const c of cands) {
    try { if (fs.existsSync(c)) return c } catch (e) {}
  }
  try { fs.mkdirSync('/sdcard/DSHxpandh', { recursive: true }) } catch (e) {}
  return '/sdcard/DSHxpandh'
}
const ROOT = pickRoot()

function sh(cmd, timeoutMs) {
  return new Promise(function (resolve) {
    try {
      execFile('sh', ['-c', cmd], { timeout: timeoutMs || 20000 }, function (err, stdout, stderr) {
        resolve({ ok: !err, out: String(stdout || '') + String(stderr || '') })
      })
    } catch (e) { resolve({ ok: false, out: String(e.message) }) }
  })
}

// 心跳：让模块知道 DSH 这边活着。模块读 dsh-alive 的时间戳判断「已连接 / 未连接」。
// 与 alive-<pkg> 同一套思路，只是方向反过来：那边是模块告诉 App，这边是 App 告诉模块。
// 有 root 且隐身开着时，清掉无 root 时期留在 /sdcard 的共享命令残留。
// 为什么要有这个：免 root 时命令只能靠共享文件传递，文件名虽是哈希、内容是混淆的，
// 但东西还在盘上。一旦有了 root（改走目标私有目录）又开着隐身，这些残留就该消失。
let lastScrub = 0
async function maybeScrub() {
  const now = Date.now()
  if (now - lastScrub < 60000) return 0
  lastScrub = now
  if (!fs.existsSync(ROOT + '/stealth')) return 0
  const st = await probe(false)
  if (!st.root) return 0
  let n = 0
  try {
    for (const name of fs.readdirSync(ROOT)) {
      const isCmd = (name.slice(0, 2) === 'c-' && name.slice(-4) === '.bin') || (name.slice(0, 4) === 'cmd-' && name.slice(-5) === '.json')
      const isStray = name.startsWith('probe-') || name.startsWith('alive-')
      if (!isCmd && !isStray) continue
      if (!isCmd && name.indexOf(PKG) >= 0) continue
      try { fs.unlinkSync(ROOT + '/' + name); n++ } catch (e) {}
    }
  } catch (e) {}
  if (n > 0) {
    try { console.warn('[local-dshxpandh] 隐身 + root：已清理 ' + n + ' 个无 root 时期残留') } catch (e) {}
  }
  return n
}

// 诊断痕迹：apply 一进来就写一条。有了它，「插件没加载」和「加载了但心跳失败」
// 就能分开看——以前心跳写不进去是静默的，什么都看不到。
// stealth 开着时不写：那时候本来就不该在任何地方留痕。
function markHit(tag) {
  try {
    if (fs.existsSync(ROOT + '/stealth')) return
    fs.mkdirSync(ROOT, { recursive: true })
    fs.writeFileSync(ROOT + '/apply-hit', String(Date.now()) + ' ' + tag + '\n', 'utf8')
  } catch (e) {}
}

function beat() {
  try {
    fs.mkdirSync(ROOT, { recursive: true })
    // 只写时间戳：模块侧只用第一行；不带任何可识别字串，被扫到也只是个数字文件。
    fs.writeFileSync(ROOT + '/dsh-alive', String(Date.now()) + '\n', 'utf8')
    maybeScrub().catch(function () {})
  } catch (e) {
    // 以前这里直接吞掉：心跳写失败等于失联，却留不下任何线索。
    try { fs.writeFileSync(ROOT + '/beat-err', Date.now() + ' ' + String((e && e.message) || e) + '\n', 'utf8') } catch (e2) {}
  }
}
let cache = null
let cacheAt = 0

async function probe(force) {
  const now = Date.now()
  if (!force && cache && now - cacheAt < 60000) return cache
  const hasRoot = await sh('command -v psh >/dev/null && echo yes', 5000)
  const root = String(hasRoot.out).indexOf('yes') >= 0
  let installed = false
  let version = ''
  if (root) {
    const p = await sh('psh "pm list packages 2>/dev/null | grep -i dshxpandh" 2>/dev/null', 20000)
    installed = String(p.out).indexOf('dshxpandh') >= 0
    if (installed) {
      const v = await sh('psh "dumpsys package ' + PKG + ' 2>/dev/null | grep versionName" 2>/dev/null', 20000)
      const m = String(v.out).match(/versionName=([0-9.]+)/)
      if (m) version = m[1]
    }
  }
  cache = { root: root, installed: installed, version: version, dir: ROOT }
  cacheAt = now
  return cache
}

function scripts() {
  const d = ROOT + '/scripts'
  const out = []
  try {
    for (const n of fs.readdirSync(d)) {
      if (n.slice(-3) !== '.js') continue
      let size = 0
      try { size = fs.statSync(d + '/' + n).size } catch (e) {}
      out.push({ name: n, size: size, off: fs.existsSync(d + '/' + n + '.off') })
    }
  } catch (e) {}
  return out
}

// 写命令通道：base64 传，避免引号嵌套把 JSON 弄坏。
// psh 用 execFile 直接传参，不经过 shell —— 少一层引号就少一类坑。
function psh(args) {
  return new Promise(function (resolve) {
    try {
      execFile('psh', args, { timeout: 30000 }, function (err, stdout, stderr) {
        resolve({ ok: !err, out: String(stdout || '') + String(stderr || '') })
      })
    } catch (e) { resolve({ ok: false, out: String(e.message) }) }
  })
}

const XOR_KEY = 'xhk.2026.offline'

// 文件名只留包名的 SHA-1 前 8 位：扫目录的人反推不出是哪个 App。
function cmdTag(pkg) {
  return crypto.createHash('sha1').update(pkg, 'utf8').digest('hex').slice(0, 8)
}

// base64 + XOR。防的是「在 /sdcard 里扫明文 js 关键词」，不是防逆向。
function obfuscate(s) {
  const b = Buffer.from(s, 'utf8')
  const k = Buffer.from(XOR_KEY, 'utf8')
  for (let i = 0; i < b.length; i++) b[i] = b[i] ^ k[i % k.length]
  return b.toString('base64')
}

// 有 root：写目标 App 私有目录（不落 /sdcard，隐身开着也能用，跟改造前一致）。
async function writePrivate(pkg, payload) {
  const b64 = Buffer.from(payload, 'utf8').toString('base64')
  const dest = '/data/user/0/' + pkg + '/files/dshxpandh.cmd.json'
  const inner = 'mkdir -p /data/user/0/' + pkg + '/files; echo ' + b64 + ' | base64 -d > ' + dest
  const pr = await sh('psh "pidof ' + pkg + '" 2>/dev/null', 10000)
  const pid = String(pr.out).trim().split(/\s+/)[0]
  if (pid && /^[0-9]+$/.test(pid)) {
    const r = await sh("psh \"nsenter -t " + pid + " -m -- sh -c '" + inner + "'\" 2>&1", 25000)
    const o = String(r.out)
    if (o.indexOf('Error') < 0 && o.indexOf('denied') < 0) return { ok: true, how: 'private file (live pid ' + pid + ')' }
  }
  const r2 = await sh('psh "' + inner + '" 2>&1', 25000)
  const o2 = String(r2.out)
  const bad = o2.indexOf('Permission denied') >= 0 || o2.indexOf('Error') >= 0
  return { ok: !bad, how: bad ? ('private file failed: ' + o2.slice(0, 80)) : 'private file (cold)' }
}

async function writeCmd(pkg, jsCode, match, value) {
  const payload = JSON.stringify({ ts: Date.now(), js: jsCode, match: match || '', value: value || '' })
  const stealth = fs.existsSync(ROOT + '/stealth')
  const st = await probe(false)
  // 有 root 就走私有目录：不碰 /sdcard，隐身开着也照常可用。
  if (st.root) {
    const r = await writePrivate(pkg, payload)
    if (r.ok) return r
    // 私有目录失败（SELinux 等）再退到共享文件
  }
  // 免 root：写共享文件。文件名只留包名哈希，内容混淆，模块读完即删。
  // 隐身开着就不写：这时候宁可下发不了，也不在存储里留可被扫到的痕迹。
  if (stealth) {
    return { ok: false, how: '隐身已开且没有 root：为不留痕迹拒绝写共享命令文件（有 root 时会改走私有目录）' }
  }
  try {
    fs.mkdirSync(ROOT, { recursive: true })
    fs.writeFileSync(ROOT + '/c-' + cmdTag(pkg) + '.bin', obfuscate(payload), 'utf8')
    return { ok: true, how: 'shared ' + ROOT + '/c-' + cmdTag(pkg) + '.bin（模块读完即删）' }
  } catch (e) {
    return { ok: false, how: String(e.message) }
  }
}




function textOut() { return { schema: { type: 'string' }, render: function (a, v) { return [{ type: 'text', text: String(v) }] } } }

export function apply(ctx) {
  markHit('apply-start')   // 一进来就留痕：只要插件被加载，这个文件就会出现
  const disposers = []
  function safeRegister(def) {
    try {
      // 「打开 DSH 就自动连上」：任何工具被调用都顺手刷新一次心跳。
      // 只靠 60 秒定时器的话，刚打开 DSH 那段时间模块侧看不到连接。
      const origExec = def && def.execute
      if (typeof origExec === 'function') {
        def.execute = async function () {
          beat()
          return await origExec.apply(this, arguments)
        }
      }
      const d = ctx.tools.register(def)
      if (typeof d === 'function') disposers.push(d)
      return true
    } catch (e) {
      try { console.warn('[local-dshxpandh] 注册失败 ' + (def && def.name) + ': ' + (e && e.message)) } catch (e2) {}
      return false
    }
  }

  safeRegister({
    name: 'dshxpandh_status',
    description: '查对接状态：模块版本、root 可用性、控制目录、注入总开关、脚本数。做任何 DSHxpandh 操作前先查，或用户问「连没连 / 能不能用」。',
    parameters: { type: 'object', properties: {} },
    output: textOut(),
    async execute() {
      const s = await probe(true)
      const lines = []
      lines.push('模块: ' + (s.installed ? ('已装 ' + (s.version || '')) : '未安装'))
      lines.push('Root: ' + (s.root ? 'psh 可用' : '不可用（注入改走共享文件；装 APK 需要它）'))
      lines.push('控制目录: ' + s.dir + (fs.existsSync(s.dir) ? '' : ' (不存在)'))
      lines.push('注入总开关: ' + (fs.existsSync(s.dir + '/off') ? '已关' : '开'))
      const ss = scripts()
      lines.push('脚本层: ' + ss.length + ' 个' + (ss.length ? ' (' + ss.map(function (x) { return x.name + (x.off ? '[停]' : '') }).join(', ') + ')' : ''))
      return lines.join('\n')
    }
  })

  safeRegister({
    name: 'dshxpandh_script',
    description: '脚本层：往控制目录 scripts/ 放 .js，3 秒内自动注入所有已登记 WebView（标准 / 腾讯 XWeb / Chromium），不用重启 App。改页面、去广告弹窗、改样式、拦请求优先用它。脚本要幂等（先判 window.__done）。',
    parameters: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['list', 'add', 'remove', 'off', 'on'], description: 'list / add / remove / off / on' },
        name: { type: 'string', description: '脚本名，须以 .js 结尾' },
        code: { type: 'string', description: 'add 时的 JS 源码' }
      },
      required: ['action']
    },
    output: textOut(),
    async execute(args) {
      const dir = ROOT + '/scripts'
      const act = String(args && args.action || '')
      if (act === 'list') {
        const ss = scripts()
        return ss.length ? ss.map(function (x) { return x.name + '  ' + x.size + 'B' + (x.off ? '  [停]' : '') }).join('\n') : '脚本层为空（' + dir + '）'
      }
      const nm = String(args && args.name || '').trim()
      if (!nm || nm.slice(-3) !== '.js') throw new Error('name 必填且以 .js 结尾')
      if (nm.indexOf('/') >= 0 || nm.indexOf('..') >= 0) throw new Error('name 不能含路径')
      try { fs.mkdirSync(dir, { recursive: true }) } catch (e) {}
      const full = dir + '/' + nm
      if (act === 'add') {
        const code = String(args && args.code || '')
        if (!code) throw new Error('add 需要 code')
        fs.writeFileSync(full, code, 'utf8')
        try { if (fs.existsSync(full + '.off')) fs.unlinkSync(full + '.off') } catch (e) {}
        return '已写入 ' + full + '（' + code.length + ' 字节），3 秒内注入到已登记页面'
      }
      if (act === 'remove') {
        if (!fs.existsSync(full)) throw new Error('不存在: ' + nm)
        fs.unlinkSync(full)
        try { if (fs.existsSync(full + '.off')) fs.unlinkSync(full + '.off') } catch (e) {}
        return '已删除 ' + nm
      }
      if (act === 'off') {
        if (!fs.existsSync(full)) throw new Error('不存在: ' + nm)
        fs.writeFileSync(full + '.off', String(Date.now()), 'utf8')
        return '已停用 ' + nm
      }
      if (act === 'on') {
        if (fs.existsSync(full + '.off')) fs.unlinkSync(full + '.off')
        return '已恢复 ' + nm
      }
      throw new Error('action 只能是 list/add/remove/off/on')
    }
  })

  safeRegister({
    name: 'dshxpandh_inject',
    description: '命令层：只对指定 App 下发一次性 JS。有 root 写该 App 私有目录，隐身开着也能用；没 root 写控制目录共享文件，隐身开着会被拒绝。要全部 App 生效用 dshxpandh_script。',
    parameters: {
      type: 'object',
      properties: {
        pkg: { type: 'string', description: '目标 App 包名' },
        js: { type: 'string', description: '注入的 JS' },
        match: { type: 'string', description: '可选：JS 里的 __MATCH__ 占位替换成它' },
        value: { type: 'string', description: '可选：JS 里的 __VAL__ 占位替换成它' },
        clear: { type: 'boolean', description: '清空该 App 的注入' }
      },
      required: ['pkg']
    },
    output: textOut(),
    async execute(args) {
      const pkg = String(args && args.pkg || '').trim()
      if (!pkg) throw new Error('pkg 不能为空')
      // 不再要求 root：命令走控制目录共享文件，模块侧通过 provider 取。
      if (args.clear) {
        const r = await sh('psh "rm -f /data/user/0/' + pkg + '/files/dshxpandh.cmd.json" 2>&1', 20000)
        return '已清空 ' + pkg + '\n' + String(r.out).trim()
      }
      const code = String(args && args.js || '')
      if (!code) throw new Error('js 不能为空（或 clear=true）')
      const w = await writeCmd(pkg, code, args.match, args.value)
      if (!w.ok) throw new Error('写入失败（root 或 SELinux 拒绝）')
      return '注入已写入 ' + pkg + '（' + w.how + '）\n页面绘制时生效；日志用 dshxpandh_logs 看'
    }
  })

  safeRegister({
    name: 'dshxpandh_logs',
    description: '读模块 emit 的结果行（hooked / IN / OUT / OVR / ACT / js-attach / ready）。模块不落盘，从 lspd 模块日志读。',
    parameters: {
      type: 'object',
      properties: {
        grep: { type: 'string', description: '过滤关键词（包名/类名/OVR）' },
        lines: { type: 'number', description: '返回行数，默认 40' }
      },
      required: []
    },
    output: textOut(),
    async execute(args) {
      const n = Math.max(1, Math.min(200, Number(args && args.lines || 40)))
      const kw = String(args && args.grep || '').trim()
      // 关键词在 node 侧过滤：拼进 shell 会被空格/引号拆坏（原来用双引号包，含空格就失效）
      const base = 'tail -n 4000 /data/adb/lspd/log/modules*.log 2>/dev/null'
      const r = await sh('psh ' + JSON.stringify(base) + ' 2>&1', 30000)
      let rows = String(r.out || '').split(String.fromCharCode(10))
      if (kw) {
        const k = kw.toLowerCase()
        rows = rows.filter(function (l) { return l.toLowerCase().indexOf(k) >= 0 })
      }
      const t = rows.slice(-n).join(String.fromCharCode(10)).trim()
      return t || '没有日志（模块未激活，或日志被 max_logs 封顶）'
    }
  })

  // ── App 原生界面：状态条 + 面板 ──
  ctx.inject(['dshGo'], (c) => {
    c.dshGo.strip('dshxpandh-strip', () => {
      const s = cache
      if (!s) return { text: '注入能力 待检测' }
      if (!s.installed) return { text: '注入能力 未安装' }
      return { text: '注入能力 ' + (s.version || '已装') }
    })

    c.dshGo.panel('dshxpandh-panel', {
      view: () => {
        const s = cache
        const kids = []
        if (!s) {
          kids.push({ type: 'text', text: '还没检测。点「重新检测」。' })
        } else {
          kids.push({ type: 'metric', label: '模块', value: s.installed ? (s.version || '已安装') : '未安装' })
          kids.push({ type: 'metric', label: 'Root', value: s.root ? '可用' : '不可用（注入走共享文件）' })
          kids.push({ type: 'metric', label: '脚本', value: String(scripts().length) })
          kids.push({ type: 'text', text: s.installed
            ? '在 LSPosed 里启用本模块并勾选目标 App，然后重启那个 App。启用这步只能手动，系统没给接口。'
            : '模块未装。点「打开下载页」取最新 APK，装完回 LSPosed 启用。' })
        }
        kids.push({ type: 'text', text: '能力：抓明文 · 改参数 · 改返回值 · 注入 JS · 读 Cookie · 调内部方法' })
        kids.push({ type: 'actions', items: [
          { label: '检测并上报', action: 'recheck' },
          { label: '打开模块', action: 'openModule' },
          { label: '打开下载页', action: 'openRelease' }
        ] })
        return { type: 'stack', children: kids }
      },
      actions: {
        recheck: async () => {
          // 手动入口：立刻写一次心跳（告诉模块「我还在」），再查模块状态。
          // 自动心跳只在 DSH 引擎跑着时写；被系统冻结时这里能补一次。
          beat()
          const s = await probe(true)
          if (!s.root) return { toast: '已上报心跳；无 root（命令走共享文件）' }
          return { toast: s.installed ? ('已上报心跳；模块 ' + (s.version || '已装')) : '已上报心跳；未检测到模块' }
        },
        openModule: async () => {
          const r = await sh('psh "am start -n ' + ACTIVITY + '" 2>&1', 15000)
          return { toast: String(r.out).indexOf('Error') >= 0 ? '打不开，模块可能没装' : '已打开模块设置' }
        },
        openRelease: async () => {
          const r = await sh('psh "am start -a android.intent.action.VIEW -d ' + RELEASE + '" 2>&1', 15000)
          return { toast: String(r.out).indexOf('Error') >= 0 ? '没有可打开网页的应用' : '已打开下载页' }
        }
      }
    })
  })

  beat()
  const beatTimer = setInterval(beat, 60000)
  if (typeof beatTimer.unref === 'function') beatTimer.unref()


  // 能力声明写进系统提示：插件装上就生效，卸载就消失，不依赖外部规则文件。
  // 这一段是静态文本（不含运行状态），避免每轮重写整个系统提示。
  ctx.inject(['systemPrompt'], (c) => {
    c.systemPrompt.section({
      name: 'dshxpandh-abilities',
      order: 88,
      text: [
        '本机装有 DSHxpandh（LSPosed 模块），能看见并操作别的 App 的网页层：',
        '改 DOM 与样式、去广告弹窗、拦 fetch/XHR、改接口返回、hook 页面函数、读 localStorage；',
        '覆盖标准 WebView / 腾讯 XWeb / Chromium。新功能不必等工具更新，把 .js 放脚本目录 3 秒内自动注入；',
        '只针对单个 App 或要传参，用 dshxpandh_inject（__MATCH__ / __VAL__ 占位）。',
        '改 Java 方法（参数、返回值、静态方法、Cookie）要在模块 UI 配方页配 class + method + mode，不在这套工具里。',
        '用户说「改某个 App 的页面 / 去广告 / 改样式 / 拦请求 / 抓接口」时：先 dshxpandh_status 查通道，再 dshxpandh_script。',
        '边界：模块须在 LSPosed 启用并勾选目标 App。',
      ].join(''),
    })
  })

  probe(false).catch(function () {})

  markHit('apply-ok')   // 走到这里 = 工具注册、心跳、面板全部挂上了

  if (typeof ctx.effect === 'function') {
    // 注意：ctx.effect 收的是「返回清理函数的工厂」，不是清理函数本身。
    // 以前直接把清理逻辑当参数传进去，它会被当场执行 → 定时器刚设好就被清掉，
    // 表现是 apply 走完了、心跳只跳一次（14:36:23 那一跳）然后再也不跳。
    ctx.effect(function () {
      return function () {
        try { clearInterval(beatTimer) } catch (e) {}
        for (const d of disposers) { try { if (typeof d === 'function') d() } catch (e) {} }
      }
    })
  }
}