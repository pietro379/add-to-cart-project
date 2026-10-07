(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
  const money = (value) => usd.format(Number(value));

  const TYPE_LABELS = {
    CLOTHING: "Giyim", COSMETICS: "Kozmetik", ELECTRONICS: "Elektronik", PHONE: "Telefon", STATIONERY: "Kırtasiye",
  };
  const ICONS = {
    CLOTHING: "M8 3 4 6l2 4 2-1v12h8V9l2 1 2-4-4-3c-.5 1.5-2 2.5-4 2.5S8.5 4.5 8 3Z",
    COSMETICS: "M10 3h4v4h-4zM9 7h6l1 3v10a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V10z",
    ELECTRONICS: "M4 5h16v11H4zM2 19h20M9 16v3M15 16v3",
    PHONE: "M8 2h8a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1ZM11 18h2",
    STATIONERY: "M4 20l4-1 11-11-3-3L5 16zM14 6l3 3",
  };
  const LOW_STOCK = 5;
  const BILL_THRESHOLD = 200;

  let state = null;
  const pendingQty = new Map(); // ürün kartlarındaki adet seçimleri

  /* ---------- API ---------- */

  async function api(path, body) {
    const options = body === undefined
      ? { method: "GET" }
      : { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
    const response = await fetch(path, options);
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || "İstek başarısız oldu.");
    return data;
  }

  async function act(path, body) {
    try {
      const next = await api(path, body);
      render(next);
      if (next.message) toast(next.message);
    } catch (error) {
      toast(error.message, true);
    }
  }

  /* ---------- Yardımcılar ---------- */

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function icon(type, size) {
    const wrap = el("span", `type-icon tone-${type}`);
    wrap.innerHTML = `<svg viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true"><path d="${ICONS[type] || ICONS.ELECTRONICS}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
    return wrap;
  }

  function stepper(value, min, max, onChange, label) {
    const box = el("div", "stepper");
    box.setAttribute("role", "group");
    box.setAttribute("aria-label", label);
    const minus = el("button", null, "−");
    const plus = el("button", null, "+");
    const out = el("output", null, String(value));
    minus.type = plus.type = "button";
    minus.setAttribute("aria-label", "Azalt");
    plus.setAttribute("aria-label", "Artır");
    minus.disabled = value <= min;
    plus.disabled = value >= max;
    minus.addEventListener("click", () => onChange(value - 1));
    plus.addEventListener("click", () => onChange(value + 1));
    box.append(minus, out, plus);
    return box;
  }

  function toast(message, isError) {
    const node = el("div", `toast${isError ? " error" : ""}`, message);
    $("toasts").append(node);
    setTimeout(() => node.remove(), isError ? 4500 : 2500);
  }

  function percent(rate) {
    return `%${Math.round(Number(rate) * 100)}`;
  }

  /* ---------- Çizim ---------- */

  function render(next) {
    state = next;
    $("customer").value = state.customer;
    $("undo").disabled = !state.canUndo;
    $("redo").disabled = !state.canRedo;
    renderProducts();
    renderCart();
  }

  function renderProducts() {
    $("products").replaceChildren(...state.products.map((p) => {
      const card = el("li", "product");

      const top = el("div", "product-top");
      top.append(icon(p.type, 24));
      const stock = el("span", "stock");
      if (p.available === 0) {
        stock.classList.add("out");
        stock.textContent = p.inCart > 0 ? "Tümü sepette" : "Tükendi";
      } else if (p.available <= LOW_STOCK) {
        stock.classList.add("low");
        stock.textContent = `Son ${p.available} adet`;
      } else {
        stock.textContent = `Stok: ${p.available}`;
      }
      top.append(stock);

      const info = el("div");
      info.append(el("h3", null, p.name), el("span", "type", TYPE_LABELS[p.type] || p.type));

      const priceRow = el("div");
      priceRow.append(el("div", "price", money(p.price)));
      if (p.type === "PHONE") priceRow.append(el("div", "note", "Yüzde indirim uygulanmaz"));

      const actions = el("div", "product-actions");
      const qty = Math.min(pendingQty.get(p.id) || 1, Math.max(p.available, 1));
      actions.append(stepper(qty, 1, Math.max(p.available, 1), (v) => {
        pendingQty.set(p.id, v);
        renderProducts();
      }, `${p.name} adedi`));
      const add = el("button", "btn btn-soft", p.inCart > 0 ? `Ekle (${p.inCart})` : "Sepete ekle");
      add.type = "button";
      add.disabled = p.available === 0;
      add.addEventListener("click", () => {
        pendingQty.delete(p.id);
        act("/api/cart/add", { productId: p.id, quantity: qty });
      });
      actions.append(add);

      card.append(top, info, priceRow, actions);
      return card;
    }));
  }

  function renderCart() {
    const items = state.cart;
    const stockById = new Map(state.products.map((p) => [p.id, p.stock]));
    $("cart-count").textContent = `${state.itemCount} ürün`;
    $("cart-empty").hidden = items.length > 0;
    $("totals").hidden = items.length === 0;
    $("checkout").disabled = items.length === 0;

    $("cart-items").replaceChildren(...items.map((item) => {
      const row = el("li", "cart-item");
      const controls = el("div", "controls");
      controls.append(
        stepper(item.quantity, 0, stockById.get(item.id) ?? item.quantity,
          (v) => act("/api/cart/quantity", { productId: item.id, quantity: v }), `${item.name} adedi`),
        el("span", "unit", `${money(item.unitPrice)} / adet`));
      const remove = el("button", "remove", "Kaldır");
      remove.type = "button";
      remove.addEventListener("click", () => act("/api/cart/remove", { productId: item.id }));
      controls.append(remove);
      row.append(icon(item.type, 18), el("span", "name", item.name), el("span", "line", money(item.lineTotal)), controls);
      return row;
    }));

    const t = state.totals;
    $("t-subtotal").textContent = money(t.subtotal);
    $("row-user").hidden = Number(t.userDiscount) === 0;
    $("t-user-label").textContent = `Üye indirimi (${percent(t.userDiscountRate)}, telefon hariç)`;
    $("t-user").textContent = `−${money(t.userDiscount)}`;
    $("row-bill").hidden = Number(t.billDiscount) === 0;
    $("t-bill").textContent = `−${money(t.billDiscount)}`;
    $("t-total").textContent = money(t.total);
    $("modal-total").textContent = money(t.total);
    $("mobile-bar").hidden = items.length === 0;
    $("mobile-count").textContent = `${state.itemCount} ürün`;
    $("mobile-total").textContent = money(t.total);

    // Bir sonraki $5 indirime ne kadar kaldığını göster.
    const afterPercentage = Number(t.subtotal) - Number(t.userDiscount);
    const remaining = BILL_THRESHOLD - (afterPercentage % BILL_THRESHOLD);
    $("hint").hidden = items.length === 0;
    $("hint").textContent = `${money(remaining)} daha eklerseniz $5 ek indirim kazanırsınız.`;
  }

  function renderReceipt(receipt) {
    $("receipt-payment").textContent = receipt.paymentMessage;
    $("receipt-id").textContent = receipt.invoiceId;
    $("receipt-customer").textContent = `${receipt.customerName} · ${receipt.customerLabel}`;
    $("receipt-items").replaceChildren(...receipt.items.map((item) => {
      const tr = el("tr");
      tr.append(el("td", null, item.name), el("td", "num", String(item.quantity)), el("td", "num", money(item.lineTotal)));
      return tr;
    }));
    const t = receipt.totals;
    const rows = [["Ara toplam", money(t.subtotal)]];
    if (Number(t.userDiscount) > 0) rows.push([`Üye indirimi (${percent(t.userDiscountRate)})`, `−${money(t.userDiscount)}`, "discount"]);
    if (Number(t.billDiscount) > 0) rows.push(["$200 indirimi", `−${money(t.billDiscount)}`, "discount"]);
    rows.push(["Ödenen", money(t.total), "grand"]);
    $("receipt-totals").replaceChildren(...rows.map(([label, value, cls]) => {
      const div = el("div", cls);
      div.append(el("dt", null, label), el("dd", null, value));
      return div;
    }));
  }

  /* ---------- Ödeme ---------- */

  const modal = $("checkout-modal");
  const form = $("checkout-form");
  const card = $("card");

  function paymentMethod() {
    return form.elements.payment.value;
  }

  function showError(message) {
    $("checkout-error").textContent = message;
    $("checkout-error").hidden = !message;
  }

  $("checkout").addEventListener("click", () => {
    form.hidden = false;
    $("receipt").hidden = true;
    showError("");
    card.classList.remove("invalid");
    modal.showModal();
    $("name").focus();
  });

  form.addEventListener("change", () => {
    $("card-field").hidden = paymentMethod() !== "card";
  });

  // Kart numarasını 4'lü gruplar halinde biçimlendir.
  card.addEventListener("input", () => {
    const digits = card.value.replace(/\D/g, "").slice(0, 19);
    card.value = digits.replace(/(\d{4})(?=\d)/g, "$1 ");
    card.classList.remove("invalid");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    showError("");
    const pay = $("pay");
    pay.disabled = true;
    try {
      await api("/api/customer", { customer: $("customer").value, name: $("name").value });
      const receipt = await api("/api/checkout", { payment: paymentMethod(), cardNumber: card.value });
      renderReceipt(receipt);
      form.hidden = true;
      $("receipt").hidden = false;
      card.value = "";
      render(await api("/api/state"));
    } catch (error) {
      showError(error.message);
      if (paymentMethod() === "card") card.classList.add("invalid");
    } finally {
      pay.disabled = false;
    }
  });

  modal.addEventListener("click", (event) => {
    if (event.target === modal || event.target.closest("[data-close]")) modal.close();
  });

  /* ---------- Diğer olaylar ---------- */

  $("customer").addEventListener("change", (event) => act("/api/customer", { customer: event.target.value }));
  $("undo").addEventListener("click", () => act("/api/undo", {}));
  $("redo").addEventListener("click", () => act("/api/redo", {}));

  document.addEventListener("keydown", (event) => {
    if (modal.open || event.target.closest("input, select, textarea")) return;
    const mod = event.ctrlKey || event.metaKey;
    if (!mod) return;
    const key = event.key.toLowerCase();
    if (key === "z" && !event.shiftKey && state?.canUndo) {
      event.preventDefault();
      act("/api/undo", {});
    } else if ((key === "y" || (key === "z" && event.shiftKey)) && state?.canRedo) {
      event.preventDefault();
      act("/api/redo", {});
    }
  });

  api("/api/state").then(render).catch((error) => toast(error.message, true));
})();
