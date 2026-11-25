(function () {
  ['contextmenu','mousedown','mouseup','touchstart','touchend'].forEach(function (e) {
    try {
      document.addEventListener(e, function (ev) { ev.stopPropagation(); }, true);
    } catch (x) {}
  });
})();
