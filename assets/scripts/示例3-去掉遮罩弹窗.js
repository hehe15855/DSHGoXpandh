// 示例：把常见的遮罩层和弹窗隐藏掉。
// 只在当前页面生效，刷新即恢复。
(function () {
  var selectors = ['[class*="mask"]', '[class*="modal"]', '[class*="overlay"]', '[id*="popup"]', '[class*="popup"]'];
  selectors.forEach(function (sel) {
    try {
      var nodes = document.querySelectorAll(sel);
      for (var i = 0; i < nodes.length; i++) nodes[i].style.display = 'none';
    } catch (e) {}
  });
})();
