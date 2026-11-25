// 示例：给整个页面加反色，适合做夜间阅读。
// 不想要就把它关掉（列表里的开关）。
(function () {
  if (!document.documentElement) return;
  document.documentElement.style.filter = 'invert(1) hue-rotate(180deg)';
  var imgs = document.querySelectorAll('img');
  for (var i = 0; i < imgs.length; i++) imgs[i].style.filter = 'invert(1) hue-rotate(180deg)';
})();
