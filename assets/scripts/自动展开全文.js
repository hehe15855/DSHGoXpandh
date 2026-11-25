(function () {
  var KW = ['展开全文','展开阅读','查看全文','阅读全文','展开更多','显示全部'];
  function run() {
    document.querySelectorAll('a,button,div,span').forEach(function (el) {
      try {
        var t = (el.textContent || '').trim();
        if (t.length > 8) return;
        for (var i = 0; i < KW.length; i++) {
          if (t.indexOf(KW[i]) === 0) { el.click(); return; }
        }
      } catch (x) {}
    });
  }
  run();
  setInterval(run, 3000);
})();
