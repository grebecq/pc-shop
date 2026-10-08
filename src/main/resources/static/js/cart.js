// Корзина сайта: хранится в localStorage как { productId: количество }.
// Цены и остатки всегда берутся с сервера, браузеру доверяем только список товаров.
(function () {
  "use strict";

  var KEY = "pcshop.cart";
  var MAX_QTY = 20;

  function read() {
    try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; }
  }
  function write(cart) {
    try { localStorage.setItem(KEY, JSON.stringify(cart)); } catch (e) { /* приватный режим */ }
    updateBadge();
  }
  function count(cart) {
    return Object.keys(cart).reduce(function (sum, id) { return sum + cart[id]; }, 0);
  }
  function rub(n) {
    return n.toLocaleString("ru-RU").replace(/ /g, " ") + " ₽";
  }
  function updateBadge() {
    var n = count(read());
    document.querySelectorAll("[data-cart-count]").forEach(function (el) {
      el.textContent = n;
      el.hidden = n === 0;
    });
  }

  function add(id, qty) {
    var cart = read();
    cart[id] = Math.min((cart[id] || 0) + qty, MAX_QTY);
    write(cart);
  }

  // Кнопки «В корзину»
  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-add-to-cart]");
    if (!btn) return;
    var qtyInput = btn.hasAttribute("data-with-qty") ? document.getElementById("qty") : null;
    add(btn.getAttribute("data-add-to-cart"), qtyInput ? Math.max(1, parseInt(qtyInput.value, 10) || 1) : 1);
    var label = btn.textContent;
    btn.textContent = "В корзине ✓";
    btn.classList.add("is-done");
    setTimeout(function () { btn.textContent = label; btn.classList.remove("is-done"); }, 1400);
  });

  // +/− на странице товара
  document.querySelectorAll("[data-qty]").forEach(function (b) {
    b.addEventListener("click", function () {
      var input = document.getElementById("qty");
      var max = parseInt(input.max, 10) || MAX_QTY;
      input.value = Math.min(max, Math.max(1, (parseInt(input.value, 10) || 1) + parseInt(b.dataset.qty, 10)));
    });
  });

  // Фильтры каталога: применяем сразу при изменении
  var filters = document.querySelector("[data-autosubmit]");
  if (filters) {
    filters.addEventListener("change", function () { filters.submit(); });
  }
  var sort = document.querySelector("[data-sort]");
  if (sort && filters) {
    sort.addEventListener("change", function () {
      filters.querySelector("[name=sort]").value = sort.value;
      filters.submit();
    });
  }

  // ---------- Страница корзины ----------
  var rows = document.querySelector("[data-cart-rows]");
  if (rows) {
    renderCart();
    rows.addEventListener("click", function (e) {
      var b = e.target.closest("button[data-id]");
      if (!b) return;
      var cart = read();
      var id = b.dataset.id;
      if (b.dataset.action === "remove") delete cart[id];
      else cart[id] = Math.max(1, Math.min(MAX_QTY, (cart[id] || 1) + (b.dataset.action === "inc" ? 1 : -1)));
      write(cart);
      renderCart();
    });
    document.querySelector("[data-order-form]").addEventListener("submit", submitOrder);
  }

  var products = {};

  function renderCart() {
    var cart = read();
    var ids = Object.keys(cart);
    document.querySelector("[data-cart-empty]").hidden = ids.length > 0;
    document.querySelector("[data-cart-full]").hidden = ids.length === 0;
    if (!ids.length) return;

    fetch("/api/products/by-ids?ids=" + ids.join(","))
      .then(function (r) { return r.json(); })
      .then(function (list) {
        products = {};
        list.forEach(function (p) { products[p.id] = p; });
        // Товары, которые сняли с продажи, тихо убираем из корзины
        ids.forEach(function (id) { if (!products[id]) delete cart[id]; });
        write(cart);
        draw(cart);
      });
  }

  function draw(cart) {
    var total = 0;
    rows.innerHTML = "";
    Object.keys(cart).forEach(function (id) {
      var p = products[id];
      var qty = Math.min(cart[id], Math.max(p.stock, 0));
      var sum = p.price * cart[id];
      total += sum;
      var tr = document.createElement("tr");
      tr.innerHTML =
        '<td><a></a>' + (p.stock < cart[id] ? '<br><small class="form-error"></small>' : "") + "</td>" +
        '<td class="num">' + rub(p.price) + "</td>" +
        '<td><div class="qty"><button type="button" data-action="dec" aria-label="Меньше">−</button>' +
        '<input type="text" readonly aria-label="Количество"><button type="button" data-action="inc" aria-label="Больше">+</button></div></td>' +
        '<td class="num">' + rub(sum) + "</td>" +
        '<td><button type="button" class="remove" data-action="remove">Убрать</button></td>';
      var link = tr.querySelector("a");
      link.href = "/p/" + p.slug;
      link.textContent = p.name;
      tr.querySelector("input").value = cart[id];
      tr.querySelectorAll("button").forEach(function (b) { b.dataset.id = id; });
      var warn = tr.querySelector("small");
      if (warn) warn.textContent = "На складе только " + qty + " шт.";
      rows.appendChild(tr);
    });
    document.querySelector("[data-cart-total]").textContent = rub(total);
  }

  function submitOrder(e) {
    e.preventDefault();
    var form = e.target;
    var error = form.querySelector("[data-order-error]");
    var button = form.querySelector("button[type=submit]");
    var cart = read();
    var body = {
      customer: {
        name: form.name.value.trim(),
        phone: form.phone.value.trim(),
        address: form.address.value.trim(),
        comment: form.comment.value.trim() || null
      },
      items: Object.keys(cart).map(function (id) { return { productId: Number(id), quantity: cart[id] }; })
    };
    if (!body.customer.name || !body.customer.phone || !body.customer.address) {
      error.textContent = "Заполните имя, телефон и адрес.";
      return;
    }
    error.textContent = "";
    button.disabled = true;

    fetch("/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    })
      .then(function (r) {
        return r.json().then(function (data) {
          if (!r.ok) throw new Error(data.detail || "Не удалось оформить заказ");
          return data;
        });
      })
      .then(function (data) {
        write({});
        location.href = data.paymentUrl;
      })
      .catch(function (err) {
        error.textContent = err.message;
        button.disabled = false;
        renderCart();
      });
  }

  updateBadge();
})();
