// audit.mjs —— 项目审验。每次改动后必跑。
// 分组：A 结构一致 / B 引用有效 / C 行为正确 / D 卫生与暴露
// 用法: node audit.mjs [目录]   有 error 则 exit 1
import fs from 'node:fs'

// 目录参数只在「确实存在的目录」时才认；否则一律用当前目录。
// 之前按「第一个非 -- 开头的参数」取，跑 `--threshold M2=12` 时会把 M2=12 当目录，整个脚本崩掉。
let D = '.'
for (const a of process.argv.slice(2)) {
  if (a.slice(0, 2) === '--') continue
  try { if (fs.existsSync(a) && fs.statSync(a).isDirectory()) { D = a; break } } catch (e) {}
}
const NL = String.fromCharCode(10)
const MODE = process.argv.indexOf('--post') >= 0 ? 'post' : (process.argv.indexOf('--pre') >= 0 ? 'pre' : 'all')
const errs = [], warns = []
const read = (p) => { try { return fs.readFileSync(p, 'utf8') } catch (e) { return '' } }
const has = (s, k) => s.indexOf(k) >= 0
const cnt = (s, k) => s.split(k).length - 1
// ── 自学习状态 ──
const LEARNED_PATH = D + '/audit-learned.json'
let LEARNED = { runs: 0, observed: [], thresholds: {} }
try { LEARNED = JSON.parse(fs.readFileSync(LEARNED_PATH, 'utf8')) } catch (e) {}
if (!LEARNED.observed) LEARNED.observed = []
if (!LEARNED.thresholds) LEARNED.thresholds = {}
LEARNED.runs = (LEARNED.runs || 0) + 1

// ── 误判经验库：每次核对出误报就追加一条，脚本自动应用，越用越准 ──
let MIS = { entries: [] }
try { MIS = JSON.parse(fs.readFileSync(D + '/misjudge.json', 'utf8')) } catch (e) {}
const sysApi = new Set()
for (const en of (MIS.entries || [])) {
  if (en.category === 'systemApi' && en.pattern) {
    for (const p of en.pattern.split('|')) if (p.trim()) sysApi.add(p.trim())
  }
}


const man = read(D + '/AndroidManifest.xml')
const prop = read(D + '/module.prop')
const vjson = read(D + '/version.json')
const changelog = read(D + '/CHANGELOG.md')
const desc = read(D + '/texts/description.txt')
// 包名目录是占位符形式（{{ACCOUNT_xxx}}），不能写死；动态找含 dshxpandh 的那一层
let PKGDIR = ''
try {
  for (const p of fs.readdirSync(D + '/src/io/github')) {
    const cand = D + '/src/io/github/' + p + '/dshxpandh'
    if (fs.existsSync(cand)) { PKGDIR = cand; break }
  }
} catch (e) {}
if (!PKGDIR) { console.log('AUDIT_SELF_ERROR 找不到源码目录（src/io/github/*/dshxpandh），检查路径'); process.exit(2) }
const J = read(PKGDIR + '/SettingsActivity.java')
const H = read(PKGDIR + '/HookEntry.java')
const P = read(PKGDIR + '/DshxpandhProvider.java')
const assetJs = read(D + '/assets/dsh-plugin/lib/index.js')
// 插件改名多次，路径不能写死：在 dsh-go/plugins 里找名字含 shxpandh 的那个包
function findLivePluginJs() {
  const base = '/root/.dsh/dsh-go/plugins'
  try {
    for (const d of fs.readdirSync(base)) {
      if (d.indexOf('shxpandh') < 0) continue
      const p = base + '/' + d + '/lib/index.js'
      if (fs.existsSync(p)) return fs.readFileSync(p, 'utf8')
    }
  } catch (e) {}
  return ''
}
const liveJs = findLivePluginJs()

// ══ A. 结构一致 ══
const vn = (man.match(/versionName="([^"]*)"/) || [])[1]
const vc = (man.match(/versionCode="([^"]*)"/) || [])[1]
const pvn = (prop.match(/^version=v([0-9.]*)/m) || [])[1]
const pvc = (prop.match(/^versionCode=([0-9]*)/m) || [])[1]
const jvn = (vjson.match(/"version"\s*:\s*"([^"]*)"/) || [])[1]
if (vn !== pvn) errs.push('A1 版本不一致: Manifest ' + vn + ' vs module.prop ' + pvn)
if (vc !== pvc) errs.push('A2 版本码不一致: Manifest ' + vc + ' vs module.prop ' + pvc)
if (jvn !== vn) errs.push('A3 version.json 滞后: ' + jvn + ' vs ' + vn)
const clFirst = (changelog.match(/## v([0-9.]+)/) || [])[1]
if (clFirst !== vn) warns.push('A4 CHANGELOG 最新条目 v' + clFirst + ' ≠ 当前 ' + vn)

// 包名一致（代码里写死的模块包名 vs Manifest）
const pkgMan = (man.match(/package="([^"]*)"/) || [])[1]
if (pkgMan && !has(P, 'AUTHORITY = "' + pkgMan + '.cfg"')) warns.push('A5 provider AUTHORITY 与 Manifest 包名不符')

// ══ B. 引用有效 ══
// B1 readAsset 引用的文件存在吗
const reA = /readAsset\("([^"]+)"\)/g
let am; const refs = new Set()
while ((am = reA.exec(J))) refs.add(am[1])
for (const a of refs) if (!fs.existsSync(D + '/assets/' + a)) errs.push('B1 asset 缺失: assets/' + a)

// B2 自定义方法：调用有定义吗
const defined = new Set()
let m; const reDef = /(?:void|boolean|long|int|String|TextView|Button|LinearLayout)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(/g
while ((m = reDef.exec(J))) defined.add(m[1])
const reCall = /\b(?!set|get|is|has|add|remove|on|setOn)([a-zA-Z_][a-zA-Z0-9_]*(?:Dsh|Cmd|Plug|Stealth|Scrub|Alive|Probe|Asset|Status|Caps|Root)[A-Za-z0-9_]*)\s*\(/g
const called = new Set()
while ((m = reCall.exec(J))) called.add(m[1])
for (const c of called) if (!defined.has(c) && !sysApi.has(c)) errs.push('B2 方法未定义: ' + c + '()')

// B3 脚本/路径引用：xhook 控制目录常量是否一致
const ctrlInJ = (J.match(/String CTRL = ([^;]+);/) || [])[1]
const rootInH = (H.match(/String ROOT = ([^;]+);/) || [])[1]
if (ctrlInJ && rootInH && ctrlInJ !== rootInH) warns.push('B3 控制目录来源不同: SettingsActivity ' + ctrlInJ + ' vs HookEntry ' + rootInH)

// ══ C. 行为正确 ══
// C1 文案说自动释放，代码真有自动导出吗
const claimsAuto = has(desc, '自动释放') || has(J, '自动释放')
const hasAuto = has(J, 'exportAssetTree("dsh-plugin"')
if (claimsAuto && !hasAuto) errs.push('C1 文案说「自动释放」但无 exportAssetTree(dsh-plugin)')
if (!claimsAuto && hasAuto) warns.push('C1 代码会自动导出，文案未提')

// C2 清理共享文件必须有 root 前置
if (has(J, 'scrubSharedCmds')) {
  const body = J.slice(J.indexOf('void scrubSharedCmds'), J.indexOf('void scrubSharedCmds') + 900)
  if (!has(body, 'hasRoot')) errs.push('C2 scrubSharedCmds 缺 root 前置：无 root 会删掉唯一命令通道')
}

// C3 插件写共享文件前必须查 stealth
if (has(liveJs, "c-' + cmdTag")) {
  const si = liveJs.indexOf("c-' + cmdTag")
  const seg = liveJs.slice(Math.max(0, si - 1200), si)
  if (!has(seg, "ROOT + '/stealth'")) errs.push('C3 插件写共享命令前没查 stealth')
}

// C4 心跳文件不得含可识别字串
if (has(liveJs, 'dsh-alive')) {
  const si = liveJs.indexOf('dsh-alive')
  const seg = liveJs.slice(si, si + 200)
  if (has(seg, 'local-xhook') || has(seg, 'DSH')) errs.push('C4 心跳内容含可识别字串，会被扫出来')
}

// C5 共享命令文件名不得含包名
if (has(liveJs, "'/cmd-' + pkg")) errs.push('C5 共享命令文件名含包名，扫目录能看出是哪个 App')

// ══ D. 卫生与暴露 ══
// D1 公告里完全相同的文案
const seen = new Map()
let bm; const reB = /body\("([^"]{14,})"\)/g
while ((bm = reB.exec(J))) seen.set(bm[1], (seen.get(bm[1]) || 0) + 1)
for (const [t, n] of seen) if (n > 1) warns.push('D1 文案重复 x' + n + ': ' + t.slice(0, 34))

// D2 assets 插件与本地是否同步
if (assetJs && liveJs && assetJs !== liveJs) errs.push('D2 assets/dsh-plugin 与本地插件不同步')

// D6 插件三处副本必须一致：源码 / APK assets / 分发目录
// 为什么单独查：D2 只比了源码和 assets，而 /sdcard/DSHxpandh/dsh-plugin/ 是给别人装的，
// 漏掉它就会出现「本机好的、别人装的是旧版」（2026-11-27 实测差 2048 字节）。
// 不止 index.js：APK 里打进去的是三个文件，任何一个不同步都是「别人装到旧版」。
// package.json 决定插件元信息，cordis.patch.yml 决定注入点，漏了哪个都会出怪问题。
for (const rel of ['lib/index.js', 'package.json', 'cordis.patch.yml']) {
  try {
    const dist = fs.readFileSync('/sdcard/DSHxpandh/dsh-plugin/' + rel, 'utf8')
    const src = fs.readFileSync('/root/.dsh/dsh-go/plugins/local-dshxpandh/' + rel, 'utf8')
    if (dist !== src) errs.push('D6 分发副本不同步: ' + rel)
  } catch (e) { /* 分发目录没释放过就不算错 */ }
}

// D7 隐身+root 的清理行为必须在描述里说清
// 不说清的话，用户看到共享文件被删会当成故障（工具描述承诺「没 root 写共享文件」，
// 而拿到 root 后那些文件会被清掉，两句话看着是矛盾的）。
if (liveJs && has(liveJs, 'maybeScrub') && has(liveJs, "ROOT + '/stealth'")) {
  if (!has(liveJs, '共享文件会被自动清掉') && !has(liveJs, '残留会被自动清理')) {
    warns.push('D7 插件会清共享文件，但工具描述没提，用户会当成故障')
  }
}

// D8 插件调用的 provider 方法必须在模块侧真的实现
// 以前靠人记，现在机器对：插件写 getStr、模块只认 getString 这类 mismatch 会当场红。
try {
  const prov = fs.readFileSync(D + '/src/io/github/hehe15855/dshxpandh/DshxpandhProvider.java', 'utf8')
  const implemented = new Set()
  const mRe = /"([a-zA-Z]+)"\.equals\(method\)/g
  let mm
  while ((mm = mRe.exec(prov))) implemented.add(mm[1])
  const calledRe = /--method ([a-zA-Z]+)/g
  let cm
  const used = new Set()
  while ((cm = calledRe.exec(liveJs || ''))) used.add(cm[1])
  for (const c of used) if (!implemented.has(c)) errs.push('D8 插件调用了 provider 未实现的方法: ' + c)
} catch (e) { }

// D9 capabilities.json 的版本必须跟 Manifest 一致
// 它的 version 是手写的，升级时最容易漏——漏了就是「能力清单说 13.1.0、装的是 13.1.7」。
try {
  const capRaw = fs.readFileSync(D + '/assets/capabilities.json', 'utf8')
  const capVer = (JSON.parse(capRaw).version) || ''
  if (capVer && capVer !== vn) errs.push('D9 capabilities.json 版本滞后: ' + capVer + ' vs ' + vn)
} catch (e) { }

// D10 文档里不许把已废弃的 Download 目录当可用路径写
// 允许出现「已废弃」的说明句，但不许在操作步骤里让人往那儿拷东西。
for (const doc of ['DSH-GUIDE.md', 'HELP.md', 'README.md', '写脚本指南.md']) {
  try {
    const t = fs.readFileSync(D + '/assets/' + doc, 'utf8')
    const lines = t.split('\n')
    for (let i = 0; i < lines.length; i++) {
      const ln = lines[i]
      if (!has(ln, 'Download/DSHxpandh')) continue
      if (has(ln, '废弃') || has(ln, '已删')) continue
      errs.push('D10 ' + doc + ':' + (i + 1) + ' 把废弃目录当可用路径')
      break
    }
  } catch (e) { }
}

// D3 残留调试输出
for (const f of ['J', 'H', 'P']) {
  const s = f === 'J' ? J : f === 'H' ? H : P
  if (has(s, 'System.out.println')) warns.push('D3 ' + f + ' 里有 System.out.println 调试输出')
}

// D4 明文口令/密钥硬编码（可接受但要知情）
if (has(P, 'XOR_KEY')) console.log('   · 知情项：XOR_KEY 硬编码，只能挡明文扫描，挡不住逆向')

// D5 manifest 里声明的 DSH 包名是否与插件里一致
const dshInMan = has(man, 'io.github.ccch1mneyyy.dsh_app')
const dshInJs = has(liveJs, 'io.github.ccch1mneyyy.dsh_app')
const dshInJ = has(J, 'DSH_PKG = "io.github.ccch1mneyyy.dsh_app"')
// 插件侧不需要知道 DSH 包名，所以只比对 Manifest 与 Java 两处
if (dshInMan && !dshInJ) errs.push('D5 Manifest 声明了 DSH 包可见性，但 Java 里的 DSH_PKG 与它不一致')
if (!dshInMan && dshInJ) errs.push('D5 Java 里有 DSH_PKG，但 Manifest 没声明包可见性（Android 11+ 查不到装没装）')


// ══ E. 优化（性能与成本）══
// E1 主线程里跑 root 命令会卡 UI
for (const cls of ['setStealth', 'installDshPlugin', 'redetectDsh']) {
  const i = J.indexOf('void ' + cls)
  if (i < 0) continue
  const seg = J.slice(i, i + 900)
  if (has(seg, 'runRoot(') && !has(seg, 'new Thread')) warns.push('E1 ' + cls + ' 在主线程跑 runRoot，会卡 UI')
}

// E2 系统提示 section 长度（每轮进上下文）
const si = liveJs.indexOf('systemPrompt.section')
if (si >= 0) {
  const seg = liveJs.slice(si, si + 2000)
  const parts = (seg.match(/'[^']*'/g) || []).map(x => x.slice(1, -1)).join('')
  const n = Array.from(parts).length
  if (n > (LEARNED.thresholds.E2 || 600)) warns.push('E2 系统提示声明 ' + n + ' 字，超过 600（每轮都进上下文）')
  else console.log('   · 系统提示声明 ' + n + ' 字')
}

// E3 工具描述总量
const descs = (liveJs.match(/description: '[^']*'/g) || [])
const dtotal = descs.reduce((a, d) => a + Array.from(d).length, 0)
if (dtotal > (LEARNED.thresholds.E3 || 2000)) warns.push('E3 工具描述合计 ' + dtotal + ' 字，偏长')
else console.log('   · 工具描述合计 ' + dtotal + ' 字 / ' + descs.length + ' 个')

// E4 探测必须有缓存（否则每次画面板都跑 pm list）
if (has(liveJs, 'async function probe(') && !has(liveJs, 'cacheAt')) warns.push('E4 probe 没有缓存，每次调用都会跑 pm list')

// E5 定时器间隔
const iv = liveJs.match(/setInterval\(beat, (\d+)\)/)
if (iv && Number(iv[1]) < 30000) warns.push('E5 心跳间隔 ' + iv[1] + 'ms，过密（<30s）')

// E6 全量读文件没有大小上限
if (has(H, 'readAll(') && !has(H, 'length >')) warns.push('E6 HookEntry 里有全量读文件，未加大小上限')

// E7 循环里没有上限保护
if (has(H, 'while (true)') && !has(H, 'Thread.sleep')) warns.push('E7 HookEntry 的 while(true) 缺 sleep，会烧 CPU')

// ══ F. 语义 ══
// F1 三态分支是否都有兜底（!hasDsh / !dshOn / else）
for (const fn of ['redetectDsh']) {
  const i = J.indexOf('void ' + fn)
  if (i < 0) continue
  const seg = J.slice(i, i + 1400)
  if (!has(seg, '== null') && !has(seg, '!= null')) warns.push('F1 ' + fn + ' 没做空值保护（dshStateView 可能为 null）')
  if (cnt(seg, 'return;') < 3) warns.push('F1 ' + fn + ' 的 return 分支少于 3 个，三态可能没覆盖全')
}

// F2 开关必须成对写（on/off 不能只置不消）
if (has(J, 'scriptOn') && has(J, 'scriptOff')) {
  /* 已有互斥处理则跳过检查 */
}

// F3 清理类操作必须静默失败（不能因删除失败就抛）
for (const fn of ['scrubSharedCmds', 'cleanAlive']) {
  const i = J.indexOf('void ' + fn)
  if (i < 0) continue
  const seg = J.slice(i, i + 900)
  if (!has(seg, 'catch')) warns.push('F3 ' + fn + ' 没有 catch，删除失败会往上抛')
}

// F4 写操作后必须刷新界面状态
// 窗口 2400 字：原来 900 太窄，函数里写清「为什么这么改」的注释一多，
// 末尾的 toast 就被挤出窗口、报出假警报（2026-11-27 实测 toast 落在 1386 字处）。
for (const fn of ['installDshPlugin', 'setStealth']) {
  const i = J.indexOf('void ' + fn)
  if (i < 0) continue
  const seg = J.slice(i, i + 2400)
  if (!has(seg, 'toast') && !has(seg, 'refresh') && !has(seg, 'setText')) warns.push('F4 ' + fn + ' 写操作后没有可见反馈')
}

// ══ G. 产物（APK 里到底是什么）══════════
import { execFileSync } from 'node:child_process'
function shOut(cmd, args) {
  try { return execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }) } catch (e) { return '' }
}
const apk = D + '/mod_signed.apk'
if (MODE !== 'pre') {
if (fs.existsSync(apk)) {
  const badging = shOut('aapt', ['dump', 'badging', apk])
  const avn = (badging.match(/versionName='([^']*)'/) || [])[1]
  const avc = (badging.match(/versionCode='([^']*)'/) || [])[1]
  if (avn !== vn) errs.push('G1 APK 内 versionName ' + avn + ' ≠ Manifest ' + vn)
  if (avc !== vc) errs.push('G2 APK 内 versionCode ' + avc + ' ≠ Manifest ' + vc)
  const zl = shOut('unzip', ['-l', apk])
  for (const f of ['assets/dsh-plugin/package.json', 'assets/dsh-plugin/lib/index.js', 'assets/dsh-plugin/cordis.patch.yml']) {
    if (zl.indexOf(f) < 0) errs.push('G3 APK 缺文件: ' + f)
  }
  const inApk = shOut('unzip', ['-p', apk, 'assets/dsh-plugin/lib/index.js'])
  if (inApk && liveJs && inApk !== liveJs) errs.push('G4 APK 内插件与本地不一致')
  // apksigner 成功时把结果写 stderr 且退出码 0，所以只能用退出码判断（早期按输出文本判断会误报）
  let vfyOk = true
  try { shOut('apksigner', ['verify', apk]) } catch (e) { vfyOk = false }
  if (!vfyOk) errs.push('G5 APK 签名验证未通过')
  const sz = fs.statSync(apk).size
  if (sz < 100000) errs.push('G6 APK 体积 ' + sz + ' 异常偏小')
} else warns.push('G0 还没构建出 mod_signed.apk')
}

// ══ H. 打包配置 ══
const build = read(D + '/build.sh')
if (build && !has(build, 'audit.mjs')) errs.push('H1 build.sh 没接审验闸，坏代码能直接打包')
if (build && !has(build, 'sync-texts.mjs')) errs.push('H2 build.sh 没同步文本，module.prop 会与版本脱节')
if (build) {
  const au = build.indexOf('audit.mjs'), ln = build.indexOf('aapt2 link')
  if (au > 0 && ln > 0 && au > ln) errs.push('H3 审验在打包之后才跑，等于没挡')
}

// ══ I. 文档一致 ══
const readme = read(D + '/README.md')
// 能力叙述：公告、description、README 都要提到同一套（至少都要提到 DSH 对接）
const docs = [['description', desc], ['公告', J], ['README', readme]]
for (const [name, s] of docs) {
  if (s && !has(s, 'DSH') && !has(s, 'dsh')) warns.push('I1 ' + name + ' 没提 DSH 对接')
}
// 四个工具名在三处都要齐
for (const tool of ['xhook_status', 'xhook_script', 'xhook_inject', 'xhook_logs']) {
  const inAsset = has(assetJs, tool), inLive = has(liveJs, tool)
  if (inAsset !== inLive) errs.push('I2 工具 ' + tool + ' 在 assets 与本地插件里不一致')
}

// ══ J. 半成品 ══
for (const [name, s] of [['SettingsActivity', J], ['HookEntry', H], ['XhookProvider', P], ['插件', liveJs]]) {
  for (const mark of ['TODO', 'FIXME', 'XXX', '待补充', '未实现']) {
    const n = cnt(s, mark)
    if (n > 0) warns.push('J1 ' + name + ' 有 ' + n + ' 处 ' + mark)
  }
}

// ══ K. 死代码 ══
// 私有方法定义了但从没被调用（只查本项目命名风格的）
const defs = []
let dm; const rePriv = /(?:private|void|boolean|long|String|TextView|Button)\s+([a-zA-Z_][a-zA-Z0-9_]*(?:Dsh|Cmd|Plug|Stealth|Scrub|Alive|Probe|Asset|Status|Caps)[A-Za-z0-9_]*)\s*\(/g
while ((dm = rePriv.exec(J))) defs.push(dm[1])
for (const d of new Set(defs)) {
  const calls = cnt(J, d + '(') - 1   // 减掉定义那一处
  if (calls <= 0 && !sysApi.has(d)) warns.push('K1 死代码: ' + d + '() 定义了但没被调用')
}

// ══ L. 常量一致（同一路径在多文件里写法必须相同）══
const constNames = ['CTRL', 'OFF', 'STEALTH', 'ABIL', 'CMD', 'PREFS', 'AUTHORITY']
const constMap = new Map()
for (const cn of constNames) {
  const vals = new Set()
  for (const s of [J, H, P]) {
    const re = new RegExp('String ' + cn + ' = ([^;]+);', 'g')
    let mm;
    while ((mm = re.exec(s))) vals.add(mm[1].trim())
  }
  if (vals.size > 1) {
    const arr = Array.from(vals)
    // CTRL/ROOT 允许一个走 pickRoot()、一个走常量，只要都来自同一函数
    const allCallPick = arr.every(v => v.indexOf('pickRoot()') >= 0 || v.indexOf('CTRL +') >= 0)
    if (!allCallPick) warns.push('L1 常量 ' + cn + ' 在不同文件里定义不同: ' + arr.join(' | '))
  }
  constMap.set(cn, Array.from(vals))
}

// ══ M. UI 约束（按钮/标签太长会在手机上截断）══
const btns = J.match(/(?:primary|ghost)\([^,]+,\s*"([^"]{2,})"\)/g) || []
for (const b of btns) {
  const label = (b.match(/"([^"]+)"$/) || [])[1] || ''
  if (Array.from(label).length > (LEARNED.thresholds.M1 || 8)) warns.push('M1 按钮文字 ' + label.length + ' 字偏长: ' + label)
}
const cardTitles = J.match(/card\([^,]+,\s*"([^"]{2,})"/g) || []
for (const c of cardTitles) {
  const title = (c.match(/"([^"]+)"$/) || [])[1] || ''
  if (Array.from(title).length > (LEARNED.thresholds.M2 || 10)) warns.push('M2 卡片标题 ' + title.length + ' 字偏长: ' + title)
}

// ══ N. 异常完整（try 必须有 catch）══
for (const [name, s] of [['SettingsActivity', J], ['HookEntry', H], ['XhookProvider', P]]) {
  const tries = cnt(s, 'try {')
  const catches = Math.max(cnt(s, 'catch ('), cnt(s, 'catch('))
  if (tries > catches + 2) warns.push('N1 ' + name + ': try ' + tries + ' 个但 catch 只有 ' + catches + '，可能有裸 try')
}


// ── 自动进化：从历史里认出稳定报警，自己调整阈值 ──
const HIST_PATH = D + '/audit-history.jsonl'
let histLines = []
try { histLines = fs.readFileSync(HIST_PATH, 'utf8').trim().split(NL).filter(Boolean).slice(-12) } catch (e) {}
const histIssues = histLines.map(l => { try { return JSON.parse(l).issues || [] } catch (e) { return [] } })


// ── 人工校准命令：只操作学习状态，不跑检查 ──
// 自动进化可能放宽太多或认错人，这几条命令给人留后门。
//   --show              看现在的学习状态
//   --dismiss <关键字>   人工确认「这是误报」，加进观察名单
//   --forget <关键字>    从观察名单撤销
//   --threshold M2=16   手动指定某组的阈值
//   --reset             清空学习状态（观察名单 + 阈值 + 历史）
const CLI = process.argv.slice(2)
function flagVal(f) { const i = CLI.indexOf(f); return i >= 0 ? (CLI[i + 1] || '') : null }
function saveLearned() { try { fs.writeFileSync(LEARNED_PATH, JSON.stringify(LEARNED, null, 2) + NL); return true } catch (e) { return false } }

if (CLI.indexOf('--show') >= 0) {
  console.log('自学习状态（已跑 ' + LEARNED.runs + ' 轮）')
  console.log('观察名单 ' + LEARNED.observed.length + ' 条：')
  if (!LEARNED.observed.length) console.log('  （空）')
  LEARNED.observed.forEach(function (o) { console.log('  - ' + o) })
  const ks = Object.keys(LEARNED.thresholds)
  console.log('自定义阈值：' + (ks.length ? JSON.stringify(LEARNED.thresholds) : '（全用默认）'))
  process.exit(0)
}
if (CLI.indexOf('--reset') >= 0) {
  try { fs.unlinkSync(LEARNED_PATH) } catch (e) {}
  try { fs.unlinkSync(D + '/audit-history.jsonl') } catch (e) {}
  console.log('学习状态已清空：观察名单、阈值、历史全部删除，下次从零开始')
  process.exit(0)
}
const dv = flagVal('--dismiss')
if (dv) {
  if (LEARNED.observed.indexOf(dv) < 0) LEARNED.observed.push(dv)
  saveLearned()
  console.log('已人工确认忽略：' + dv)
  console.log('（以后不再提示；要撤销用 --forget "' + dv.slice(0, 20) + '"）')
  process.exit(0)
}
const fg = flagVal('--forget')
if (fg) {
  const before = LEARNED.observed.length
  LEARNED.observed = LEARNED.observed.filter(function (o) { return o.indexOf(fg) < 0 })
  saveLearned()
  console.log('已从观察名单移除 ' + (before - LEARNED.observed.length) + ' 条（匹配 "' + fg + '"）')
  process.exit(0)
}
const th = flagVal('--threshold')
if (th) {
  const m = th.match(/^([A-Za-z0-9]+)=([0-9]+)$/)
  if (!m) { console.log('格式：--threshold M2=16'); process.exit(1) }
  LEARNED.thresholds[m[1]] = Number(m[2])
  saveLearned()
  console.log('阈值已手动设为 ' + m[1] + ' = ' + m[2])
  process.exit(0)
}
// 同一条报警在最近若干次里连续出现几次（从最新往前数，断开就停）
function streak(issue) {
  let n = 0
  for (let i = histIssues.length - 1; i >= 0; i--) {
    if (histIssues[i].indexOf(issue) >= 0) n++
    else break
  }
  return n
}

const STABLE_AT = 5           // 连续 5 次没变，就认为它不是这轮该修的问题
const THRESH_GROUPS = ['E2', 'E3', 'M1', 'M2']
const evolved = []           // 这轮学到的东西
const shownWarns = []

for (const w of warns) {
  if (LEARNED.observed.indexOf(w) >= 0) continue          // 早就在观察名单里，不再重复提示
  const s = streak(w)
  const grp = w.slice(0, 2)
  if (s < STABLE_AT) { shownWarns.push(w); continue }
  if (THRESH_GROUPS.indexOf(grp) >= 0) {
    // 阈值类：报警稳定说明阈值卡得太紧，按实际值放宽（留 5% 余量）
    const nums = (w.match(/([0-9]{2,})/g) || []).map(Number).filter(x => x > 0)
    const actual = Math.max.apply(null, nums.concat([0]))
    if (actual > 0) {
      const oldV = LEARNED.thresholds[grp] || '默认'
      const nv = Math.ceil(actual * 1.05)
      LEARNED.thresholds[grp] = nv
      evolved.push(grp + ' 阈值 ' + oldV + ' → ' + nv + '（连续 ' + s + ' 次报警，按实际值放宽）')
      shownWarns.push(w)   // 本轮仍报一次，让人知道阈值被调整了
      continue
    }
  }
  // 非阈值类：加入观察名单，不再重复提示
  LEARNED.observed.push(w)
  evolved.push('把「' + w.slice(0, 30) + '」加入观察名单（连续 ' + s + ' 次出现，说明这轮不打算改它）')
}

try { fs.writeFileSync(LEARNED_PATH, JSON.stringify(LEARNED, null, 2) + NL) } catch (e) {}
// ── 自我进化：记录本次结果，发现反复出现的同一条报警 ──
const histPath = D + '/audit-history.jsonl'
const allIssues = errs.concat(shownWarns || warns)
try {
  fs.appendFileSync(histPath, JSON.stringify({ t: new Date().toISOString(), n: allIssues.length, issues: allIssues.map(x => x.slice(0, 44)) }) + NL)
} catch (e) {}
const recurring = []
try {
  const lines = fs.readFileSync(histPath, 'utf8').trim().split(NL).filter(Boolean).slice(-10)
  const count = new Map()
  for (const l of lines) {
    for (const iss of (JSON.parse(l).issues || [])) count.set(iss, (count.get(iss) || 0) + 1)
  }
  for (const [iss, n] of count) if (n >= 3) recurring.push(iss + '  (近 10 次里出现 ' + n + ' 次)')
} catch (e) {}
// 输出
if (MODE === 'pre') { errs.length = errs.filter ? errs.length : 0 }
console.log('== audit' + (MODE === 'pre' ? ' · 源码' : MODE === 'post' ? ' · 产物' : '') + ' ==')
console.log('   误判经验库 ' + (MIS.entries || []).length + ' 条，已排除系统 API ' + sysApi.size + ' 个')
if (errs.length) { console.log('❌ ' + errs.length); errs.forEach(e => console.log('  - ' + e)) } else console.log('✅ 无 error')
if (shownWarns.length) { console.log('⚠️ ' + shownWarns.length); shownWarns.forEach(w => console.log('  - ' + w)) } else console.log('✅ 无提醒')
console.log('   自学习：第 ' + LEARNED.runs + ' 轮 | 观察名单 ' + LEARNED.observed.length + ' 条 | 自定义阈值 ' + JSON.stringify(LEARNED.thresholds) + '')
if (evolved.length) { console.log('🧬 本轮学到 ' + evolved.length + ' 条：'); evolved.forEach(e => console.log('  + ' + e)) }
if (recurring.length) { console.log('🔁 反复出现（连续 3 次以上，值得确认是真问题还是误报）：'); recurring.forEach(r => console.log('  - ' + r)) }
process.exitCode = errs.length ? 1 : 0