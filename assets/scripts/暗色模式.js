(function () {
  if (document.getElementById('x-dark')) return;
  var s = document.createElement('style');
  s.id = 'x-dark';
  s.textContent = [
    'html,body{background:#161616 !important;color:#d8d8d8 !important}',
    'div,p,span,li,td,th,h1,h2,h3,h4,h5,h6,article,section{background-color:transparent !important;color:#d8d8d8 !important}',
    'a{color:#6ba6ff !important}',
    'input,textarea,select{background:#242424 !important;color:#e0e0e0 !important;border-color:#3a3a3a !important}'
  ].join('');
  (document.head || document.documentElement).appendChild(s);
})();
