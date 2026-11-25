(function () {
  var RATE = 2.0;
  function run() {
    document.querySelectorAll('video').forEach(function (v) {
      try { if (v.playbackRate !== RATE) v.playbackRate = RATE; } catch (x) {}
    });
  }
  run();
  setInterval(run, 2000);
})();
