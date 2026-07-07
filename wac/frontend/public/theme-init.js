(function () {
  try {
    var t = localStorage.getItem('appTheme');
    var validThemes = ['pio-light', 'pio-dark', 'blue', 'light', 'dark',
      'green', 'indigo', 'magenta', 'crimson', 'earth'];
    document.documentElement.setAttribute('data-theme',
      validThemes.indexOf(t) !== -1 ? t : 'pio-light');
  } catch (e) {}
})();
