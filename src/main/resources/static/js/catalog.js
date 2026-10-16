(function () {
  "use strict";

  var COMPARE = "pcshop.compare";
  var RECENT = "pcshop.recent";
  var MAX_COMPARE = 12;
  var MAX_RECENT = 12;

  function readList(key) {
    try {
      var list = JSON.parse(localStorage.getItem(key));
      return Array.isArray(list) ? list.map(String) : [];
    } catch (e) { return []; }
  }
  function writeList(key, list) {
    try { localStorage.setItem(key, JSON.stringify(list)); } catch (e) {  }
  }
  function csrfHeaders() {
    var token = document.querySelector("meta[name=csrf-token]");
    var header = document.querySelector("meta[name=csrf-header]");
    var h = {};
    if (token && header) h[header.content] = token.content;
    return h;
  }
  function rub(n) {
    return n.toLocaleString("ru-RU").replace(/ /g, " ") + " ₽";
  }

  var header = document.querySelector(".header");
  var loggedIn = header && header.dataset.loggedIn === "true";

  document.querySelectorAll("[data-fav].is-on [data-fav-label]").forEach(function (l) { l.textContent = "В избранном"; });

  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-fav]");
    if (!btn) return;
    if (!loggedIn) {
      location.href = "/login?next=" + encodeURIComponent(location.pathname);
      return;
    }
    var id = btn.getAttribute("data-fav");
    btn.disabled = true;
    fetch("/account/favorites/" + id, { method: "POST", headers: csrfHeaders() })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(new Error()); })
      .then(function (data) { setFavorite(id, data.favorite); })
      .catch(function () { btn.title = "Не получилось, попробуйте ещё раз"; })
      .then(function () { btn.disabled = false; });
  });

  function setFavorite(id, on) {
    document.querySelectorAll('[data-fav="' + id + '"]').forEach(function (b) {
      b.classList.toggle("is-on", on);
      var label = b.querySelector("[data-fav-label]");
      if (label) label.textContent = on ? "В избранном" : "В избранное";
    });
    var badge = document.querySelector("[data-fav-count]");
    if (badge) {
      var n = Math.max(0, (parseInt(badge.textContent, 10) || 0) + (on ? 1 : -1));
      badge.textContent = n;
      badge.hidden = n === 0;
    }
  }

  function compareUrl(ids) {
    return "/compare" + (ids.length ? "?ids=" + ids.join(",") : "");
  }

  function syncCompare() {
    var ids = readList(COMPARE);
    document.querySelectorAll("[data-compare]").forEach(function (b) {
      var on = ids.indexOf(b.getAttribute("data-compare")) >= 0;
      b.classList.toggle("is-on", on);
      b.title = on ? "Убрать из сравнения" : "Сравнить";
      var label = b.querySelector("[data-compare-label]");
      if (label) label.textContent = on ? "В сравнении" : "Сравнить";
    });
    document.querySelectorAll("[data-compare-count]").forEach(function (el) {
      el.textContent = ids.length;
      el.hidden = ids.length === 0;
    });
    document.querySelectorAll("[data-compare-link]").forEach(function (a) { a.href = compareUrl(ids); });
  }

  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-compare]");
    if (!btn) return;
    var id = btn.getAttribute("data-compare");
    var ids = readList(COMPARE);
    var i = ids.indexOf(id);
    if (i >= 0) {
      ids.splice(i, 1);
    } else {
      ids.push(id);
      if (ids.length > MAX_COMPARE) ids.shift();
    }
    writeList(COMPARE, ids);
    syncCompare();
  });

  var comparePage = document.querySelector("[data-compare-page]");
  if (comparePage) {
    var params = new URLSearchParams(location.search);
    var saved = readList(COMPARE);
    if (!params.has("ids") && saved.length) {
      location.replace(compareUrl(saved));
      return;
    }
    var shown = (params.get("ids") || "").split(",").filter(Boolean);
    comparePage.addEventListener("click", function (e) {
      var btn = e.target.closest("[data-compare-remove]");
      if (!btn) return;
      var id = btn.getAttribute("data-compare-remove");
      writeList(COMPARE, readList(COMPARE).filter(function (x) { return x !== id; }));
      location.replace(compareUrl(shown.filter(function (x) { return x !== id; })));
    });
    var onlyDiff = comparePage.querySelector("[data-only-diff]");
    if (onlyDiff) {
      onlyDiff.addEventListener("change", function () { comparePage.classList.toggle("only-diff", onlyDiff.checked); });
    }
  }

  var viewed = document.querySelector("[data-viewed]");
  var current = viewed ? viewed.getAttribute("data-viewed") : null;
  var recent = readList(RECENT);
  var recentBox = document.querySelector("[data-recent]");
  if (recentBox) {
    var show = recent.filter(function (id) { return id !== current; }).slice(0, 4);
    if (show.length) {
      fetch("/cards?ids=" + show.join(","))
        .then(function (r) { return r.ok ? r.text() : ""; })
        .then(function (html) {
          if (!html.trim()) return;
          recentBox.querySelector("[data-recent-grid]").innerHTML = html;
          recentBox.hidden = false;
          syncCompare();
        })
        .catch(function () {  });
    }
  }
  if (current) {
    recent = [current].concat(recent.filter(function (id) { return id !== current; })).slice(0, MAX_RECENT);
    writeList(RECENT, recent);
  }

  var input = document.querySelector(".search input[name=q]");
  if (input) {
    var box = document.createElement("div");
    box.className = "suggest";
    box.hidden = true;
    box.setAttribute("role", "listbox");
    input.parentNode.appendChild(box);
    input.setAttribute("autocomplete", "off");
    var timer = null;
    var lastQuery = "";
    var active = -1;

    input.addEventListener("input", function () {
      clearTimeout(timer);
      var q = input.value.trim();
      if (q.length < 2) {
        box.hidden = true;
        return;
      }
      timer = setTimeout(function () { loadSuggestions(q); }, 200);
    });

    input.addEventListener("keydown", function (e) {
      var items = box.querySelectorAll("a");
      if (box.hidden || !items.length) return;
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        active = (active + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length;
        items.forEach(function (a, i) { a.classList.toggle("is-active", i === active); });
      } else if (e.key === "Enter" && active >= 0) {
        e.preventDefault();
        location.href = items[active].href;
      } else if (e.key === "Escape") {
        box.hidden = true;
      }
    });

    input.addEventListener("focus", function () {
      if (box.children.length && input.value.trim().length >= 2) box.hidden = false;
    });
    document.addEventListener("click", function (e) {
      if (!e.target.closest(".search")) box.hidden = true;
    });
  }

  function loadSuggestions(q) {
    lastQuery = q;
    fetch("/api/products/suggest?q=" + encodeURIComponent(q))
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (list) {
        if (q === lastQuery) renderSuggestions(list, q);
      })
      .catch(function () {  });
  }

  function placeholder() {
    var span = document.createElement("span");
    span.className = "suggest__ph";
    return span;
  }

  function renderSuggestions(list, q) {
    box.innerHTML = "";
    active = -1;
    if (!list.length) {
      box.hidden = true;
      return;
    }
    list.forEach(function (p) {
      var a = document.createElement("a");
      a.href = "/p/" + p.slug;
      a.setAttribute("role", "option");
      var pic = placeholder();
      if (p.imageUrl) {
        pic = document.createElement("img");
        pic.src = p.imageUrl;
        pic.alt = "";
        pic.referrerPolicy = "no-referrer";
        pic.onerror = function () { this.replaceWith(placeholder()); };
      }
      var name = document.createElement("span");
      name.textContent = p.name;
      var price = document.createElement("span");
      price.className = "suggest__price";
      price.textContent = rub(p.price);
      a.append(pic, name, price);
      box.appendChild(a);
    });
    var all = document.createElement("a");
    all.className = "suggest__all";
    all.href = "/search?q=" + encodeURIComponent(q);
    all.textContent = "Все результаты по запросу «" + q + "»";
    box.appendChild(all);
    box.hidden = false;
  }

  syncCompare();
})();
