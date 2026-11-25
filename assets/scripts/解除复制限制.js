(function () {
  if (document.getElementById('x-copy-free')) return;
  var s = document.createElement('style');
  s.id = 'x-copy-free';
  s.textContent = '*{user-select:text !important;-webkit-user-select:text !important;-webkit-touch-callout:default !important}';
  (document.head || document.documentElement).appendChild(s);
  ['copy','cut','contextmenu','selectstart'].forEach(function (e) {
    try { document.addEventListener(e, function (ev) { ev.stopPropagation(); }, true); } catch (x) {}
  });
})();
