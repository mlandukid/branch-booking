/* Sets the colour theme before the first paint, so there is no flash of the wrong theme.
   It is a file and not inline code, because the Content-Security-Policy does not allow inline scripts.
   The Angular ThemeService follows the same rule. */
(function () {
  var theme = 'light';
  var systemDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  try {
    var stored = window.localStorage.getItem('theme');
    if (stored === 'light' || stored === 'dark') {
      theme = stored;
    } else if (systemDark) {
      theme = 'dark';
    }
  } catch (error) {
    if (systemDark) {
      theme = 'dark';
    }
  }
  document.documentElement.setAttribute('data-theme', theme);
})();