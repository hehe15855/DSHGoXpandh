// 示例：把常见的遮罩层和弹窗隐藏掉。
// 只在当前页面生效，刷新即恢复。
(function () {
  if (window.__xhMaskOff) return;   // 幂等：重复注入不加多个 observer
  window.__xhMaskOff = true;
  // 只保留足够特定的词。不要写 [class*="ad-"] 这种 —— 它会误伤 download-btn / head-top 之类
  var SEL = '[class*="mask"],[class*="modal"],[class*="overlay"],[id*="popup"],[class*="popup"],[class*="dialog"]';
  function kill() {
    try {
      var ns = document.querySelectorAll(SEL);
      for (var i = 0; i < ns.length; i++) {
        var n = ns[i];
        if (!n || !n.style) continue;
        n.style.setProperty('display', 'none', 'important');
        n.style.setProperty('visibility', 'hidden', 'important');
      }
      // 弹窗经常顺手把 body 锁死不让滚，一并解开
      if (document.body) document.body.style.setProperty('overflow', 'auto', 'important');
      if (document.documentElement) document.documentElement.style.setProperty('overflow', 'auto', 'important');
    } catch (e) {}
  }
  kill();
  // 弹窗多半是后出现的，盯着 DOM 变化继续清
  try {
    var mo = new MutationObserver(function () { kill(); });
    mo.observe(document.documentElement || document, { childList: true, subtree: true });
    setTimeout(function () { try { mo.disconnect(); } catch (e) {} }, 60000);
  } catch (e) {}
})();
