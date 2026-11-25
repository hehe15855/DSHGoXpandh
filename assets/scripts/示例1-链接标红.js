// 示例：把页面里所有链接标成红色。
// 用途：确认脚本真的注入进去了——打开任意网页，链接变红就是生效。
(function () {
  var s = document.createElement('style');
  s.textContent = 'a{color:#e91e63 !important;text-decoration:underline !important}';
  if (document.head) document.head.appendChild(s);
})();
