// Яндекс Метрика с согласием на cookie: счётчик загружается, только когда посетитель нажал «Принять».
// Решение хранится в localStorage. Скрипт подключается, только если в .env задан YANDEX_METRIKA_ID.
(function () {
  "use strict";

  var KEY = "pcshop.cookies";
  var id = Number(document.currentScript.dataset.metrika);

  function read() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function save(value) {
    try { localStorage.setItem(KEY, value); } catch (e) { /* приватный режим: спросим на следующей странице */ }
  }

  // Код счётчика из инструкции Яндекса, без Вебвизора: записи действий посетителей магазину не нужны
  function loadMetrika() {
    (function (m, e, t, r, i, k, a) {
      m[i] = m[i] || function () { (m[i].a = m[i].a || []).push(arguments); };
      m[i].l = 1 * new Date();
      k = e.createElement(t); a = e.getElementsByTagName(t)[0]; k.async = 1; k.src = r; a.parentNode.insertBefore(k, a);
    })(window, document, "script", "https://mc.yandex.ru/metrika/tag.js", "ym");
    window.ym(id, "init", { clickmap: true, trackLinks: true, accurateTrackBounce: true });
  }

  function showBanner() {
    if (document.querySelector(".cookie-banner")) return;
    var banner = document.createElement("div");
    banner.className = "cookie-banner";
    banner.setAttribute("role", "region");
    banner.setAttribute("aria-label", "Cookie");
    banner.innerHTML = '<p>Мы используем cookie и Яндекс Метрику, чтобы понимать, какими страницами пользуются. '
        + '<a href="/privacy">Подробнее</a></p>'
        + '<div class="cookie-banner__buttons"><button class="btn btn--small" type="button" data-cookie="yes">Принять</button>'
        + '<button class="btn btn--small btn--ghost" type="button" data-cookie="no">Отказаться</button></div>';
    banner.addEventListener("click", function (e) {
      var answer = e.target.getAttribute("data-cookie");
      if (!answer) return;
      save(answer);
      banner.remove();
      if (answer === "yes") loadMetrika();
    });
    document.body.appendChild(banner);
  }

  if (!id) return;
  var answer = read();
  if (answer === "yes") loadMetrika();
  else if (answer !== "no") showBanner();

  // Ссылка «Изменить решение» на странице о персональных данных
  document.addEventListener("click", function (e) {
    if (!e.target.closest("[data-cookie-reset]")) return;
    e.preventDefault();
    try { localStorage.removeItem(KEY); } catch (err) { /* ничего */ }
    showBanner();
  });
})();
