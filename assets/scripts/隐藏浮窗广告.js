(function () {
  var SEL = '[class*="float"],[class*="fixed"],[id*="float"],[id*="kefu"],[class*="kefu"],[class*="download-bar"],[class*="app-guide"]';
  function run() {
    document.querySelectorAll(SEL).forEach(function (el) {
      try {
        var s = getComputedStyle(el);
        if (s.position === 'fixed' || s.position === 'sticky') el.style.setProperty('display','none','important');
      } catch (x) {}
    });
  }
  run();
  try { new MutationObserver(run).observe(document.documentElement,{childList:true,subtree:true}); } catch (x) {}
})();
