// sync-texts.mjs —— 编译时把文本描述同步到各处，避免版本号/简介与实际脱节。
// 单一来源：AndroidManifest 的 versionName/versionCode + texts/description.txt
// 写入目标：module.prop（version / versionCode / description）
// 由 build.sh 在打 APK 前调用；手工跑也安全（幂等）。
import fs from 'node:fs'

const D = process.argv[2] || '.'
const NL = String.fromCharCode(10)
const man = fs.readFileSync(D + '/AndroidManifest.xml', 'utf8')
const vn = (man.match(/versionName="([^"]*)"/) || [])[1]
const vc = (man.match(/versionCode="([^"]*)"/) || [])[1]
if (!vn || !vc) { console.log('sync-texts: 读不到 Manifest 版本，跳过'); process.exit(0) }

let desc = ''
try { desc = fs.readFileSync(D + '/texts/description.txt', 'utf8').trim() } catch (e) {}

const p = D + '/module.prop'
let t = fs.readFileSync(p, 'utf8')
const before = t
t = t.replace(/^version=.*$/m, 'version=v' + vn)
t = t.replace(/^versionCode=.*$/m, 'versionCode=' + vc)

// description 可能是多行（texts/description.txt 里有空行分段）。
// 旧写法 /^description=.*$/m 只吃第一行，多行的续行没人管 —— 每编译一次就叠一层，
// 最后 module.prop 里堆了 8 份同样的段落。这里先整段删干净（description= 及其后续行），
// 再一次性写入。
if (desc) {
  const lines = t.split(NL)
  const kept = []
  let inDesc = false
  for (const ln of lines) {
    if (ln.startsWith('description=')) { inDesc = true; continue }
    // 续行 = 不带 key=value 结构的非空行
    if (inDesc) {
      if (ln.trim() === '') continue            // 描述里的空行
      if (!/^[A-Za-z_][A-Za-z0-9_]*=/.test(ln)) continue  // 裸续行
      inDesc = false
    }
    kept.push(ln)
  }
  // 插到 author 之后（没有就追加到末尾）
  const idx = kept.findIndex((x) => x.startsWith('author='))
  const entry = 'description=' + desc
  if (idx >= 0) kept.splice(idx + 1, 0, entry)
  else kept.push(entry)
  t = kept.join(NL).replace(/\s*$/, '') + NL
}
if (t !== before) {
  fs.writeFileSync(p, t)
  console.log('sync-texts: module.prop -> v' + vn + ' (' + vc + ')' + (desc ? ' + description ' + desc.length + ' 字' : ''))
} else {
  console.log('sync-texts: module.prop 已是最新 v' + vn + ' (' + vc + ')')
}
