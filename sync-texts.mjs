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
if (desc) t = t.replace(/^description=.*$/m, 'description=' + desc)
if (t !== before) {
  fs.writeFileSync(p, t)
  console.log('sync-texts: module.prop -> v' + vn + ' (' + vc + ')' + (desc ? ' + description ' + desc.length + ' 字' : ''))
} else {
  console.log('sync-texts: module.prop 已是最新 v' + vn + ' (' + vc + ')')
}
