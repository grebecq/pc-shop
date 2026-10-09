// Форма оплаты картой: только удобство ввода. Всё проверяет сервер, без JS форма тоже работает.
(function () {
  "use strict";

  var form = document.querySelector("[data-card-form]");
  if (!form) return;

  // 4242424242424242 → «4242 4242 4242 4242»
  function formatNumber(value) {
    return value.replace(/\D/g, "").slice(0, 19).replace(/(\d{4})(?=\d)/g, "$1 ");
  }

  // «1230» → «12/30»; слеш появляется сам после месяца, «4» сразу становится «04/»
  function formatExpiry(value, deleting) {
    var d = value.replace(/\D/g, "").slice(0, 4);
    if (d.length === 1 && d > "1") d = "0" + d;
    if (d.length > 2 || (d.length === 2 && !deleting)) return d.slice(0, 2) + "/" + d.slice(2);
    return d;
  }

  function formatCvc(value) {
    return value.replace(/\D/g, "").slice(0, 3);
  }

  // Переформатирует поле, не сбивая курсор: он остаётся после той же цифры, что и до правки
  function bind(input, format) {
    if (!input) return;
    input.addEventListener("input", function (e) {
      var deleting = (e.inputType || "").indexOf("delete") === 0;
      var caret = input.selectionStart;
      var atEnd = caret === input.value.length;
      var digitsBefore = input.value.slice(0, caret).replace(/\D/g, "").length;
      var next = format(input.value, deleting);
      if (next === input.value) return;
      input.value = next;
      var pos = next.length;
      if (!atEnd) {
        pos = 0;
        for (var seen = 0; pos < next.length && seen < digitsBefore; pos++) {
          if (/\d/.test(next.charAt(pos))) seen++;
        }
      }
      input.setSelectionRange(pos, pos);
    });
  }

  var number = form.querySelector("[name=number]");
  bind(number, formatNumber);
  bind(form.querySelector("[name=expiry]"), formatExpiry);
  bind(form.querySelector("[name=cvc]"), formatCvc);
  // После ошибки сервер возвращает номер без пробелов — показываем его снова группами
  if (number) number.value = formatNumber(number.value);
})();
