/*
 * GitHub Pages demosu için sunucusuz API.
 *
 * Pages yalnızca statik dosya sunar; Java sunucusu (org.ak.billing.web) orada çalışmaz.
 * Bu dosya app.js'in çağırdığı /api/* isteklerini yakalar ve aynı kuralları tarayıcıda uygular.
 * Yanıt biçimi ve mesajlar ShopSession / WebServer ile aynıdır; kuralların asıl kaynağı Java kodudur.
 * Para hesabı kayan nokta hatası olmasın diye kuruş (tam sayı) üzerinden yapılır.
 */
(function () {
  "use strict";

  /* ---------- Veri (database.csv ile aynı) ---------- */

  const INVENTORY = [
    { id: "b90d5b65-4884-41d1-a859-442c15d815e0", name: "BLUE MEN JACKET", stock: 20, type: "CLOTHING", cents: 1999 },
    { id: "9897265f-77f8-4079-9cd2-10f9d88c7dd7", name: "ELIDOR SHAMPOO", stock: 62, type: "COSMETICS", cents: 499 },
    { id: "b893a249-16db-4f8e-91b9-33c4a54274d9", name: "MAC AIR PRO", stock: 120, type: "ELECTRONICS", cents: 1499 },
    { id: "e7b81072-71d7-4fda-8d83-5550d3642e94", name: "SAMSUNG S3 MINI", stock: 20, type: "PHONE", cents: 99 },
    { id: "836a2062-2d33-44ed-8afd-4472f8443f6e", name: "CARTDORE Black Royal", stock: 45, type: "STATIONERY", cents: 199 },
  ];

  // Yüzde indirim oranları (InvoiceDiscounts); bir faturada yalnızca biri uygulanır.
  const CUSTOMERS = {
    GOLD: { label: "Gold kart", percent: 30 },
    SILVER: { label: "Silver kart", percent: 20 },
    AFFILIATE: { label: "Mağaza çalışanı", percent: 10 },
    LOYAL: { label: "2 yıldan eski müşteri", percent: 5 },
    NEW: { label: "Yeni müşteri", percent: 0 },
  };
  const BILL_THRESHOLD_CENTS = 20000; // her $200 için
  const BILL_DISCOUNT_CENTS = 500; //   $5 indirim

  /* ---------- Oturum durumu ---------- */

  const STORAGE_KEY = "atc-demo";
  const products = new Map(INVENTORY.map((p) => [p.id, { ...p }]));
  const cart = new Map(); // id -> adet; eklenme sırasını korur (Java'daki LinkedHashMap gibi)
  let customer = "NEW";
  let customerName = "Misafir";
  let undoStack = [];
  let redoStack = [];

  function load() {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
      if (!saved) return;
      for (const [id, stock] of Object.entries(saved.stock || {})) {
        if (products.has(id) && Number.isInteger(stock) && stock >= 0) products.get(id).stock = stock;
      }
      for (const [id, quantity] of saved.cart || []) {
        if (products.has(id) && Number.isInteger(quantity) && quantity > 0 && quantity <= products.get(id).stock) {
          cart.set(id, quantity);
        }
      }
      if (CUSTOMERS[saved.customer]) customer = saved.customer;
      if (typeof saved.customerName === "string" && saved.customerName.trim()) customerName = saved.customerName.trim();
    } catch (e) {
      /* Depolama kapalıysa demo yine çalışır; yalnızca sayfa yenilenince sıfırlanır. */
    }
  }

  function save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        stock: Object.fromEntries([...products.values()].map((p) => [p.id, p.stock])),
        cart: [...cart],
        customer,
        customerName,
      }));
    } catch (e) {
      /* yok sayılır */
    }
  }

  /* ---------- Yardımcılar ---------- */

  class ApiError extends Error {}

  const plain = (cents) => (cents / 100).toFixed(2);
  const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
  const money = (cents) => usd.format(cents / 100);

  function text(body, field) {
    const value = body[field];
    if (value === undefined || value === null) throw new ApiError(`${field} alanı gerekli.`);
    return String(value);
  }

  function integer(body, field) {
    const raw = text(body, field);
    if (!/^[+-]?\d+$/.test(raw)) throw new ApiError(`${field} bir sayı olmalı.`);
    return parseInt(raw, 10);
  }

  /* ---------- Komutlar (geri al / yinele) ---------- */

  function addCommand(product, quantity) {
    let previous;
    return {
      execute() {
        previous = cart.get(product.id);
        const total = quantity + (previous || 0);
        if (quantity <= 0 || total > product.stock) return false;
        cart.set(product.id, total);
        return true;
      },
      undo() {
        if (previous === undefined) cart.delete(product.id);
        else cart.set(product.id, previous);
      },
      describe: () => `${quantity} x ${product.name} ekleme`,
    };
  }

  function removeCommand(product) {
    let removed;
    return {
      execute() {
        removed = cart.get(product.id);
        return removed !== undefined && cart.delete(product.id);
      },
      undo() {
        if (removed !== undefined) cart.set(product.id, removed);
      },
      describe: () => `${product.name} çıkarma`,
    };
  }

  function quantityCommand(productId, quantity) {
    const product = products.get(productId);
    let previous;
    return {
      execute() {
        previous = cart.get(productId);
        if (previous === undefined || quantity < 0 || quantity === previous) return false;
        if (quantity === 0) return cart.delete(productId);
        if (quantity > product.stock) return false;
        cart.set(productId, quantity);
        return true;
      },
      undo() {
        if (previous !== undefined) cart.set(productId, previous);
      },
      describe: () => `${previous === undefined || !product ? "Ürün" : product.name} adedini ${quantity} yapma`,
    };
  }

  function run(command, failureMessage) {
    if (!command.execute()) throw new ApiError(failureMessage);
    undoStack.push(command);
    redoStack = [];
    return command.describe();
  }

  /* ---------- Fatura hesabı (MyInvoiceGenerator) ---------- */

  function totals() {
    let subtotal = 0;
    let discountable = 0; // telefon dışı ürünler
    for (const [id, quantity] of cart) {
      const p = products.get(id);
      subtotal += p.cents * quantity;
      if (p.type !== "PHONE") discountable += p.cents * quantity;
    }
    const percent = CUSTOMERS[customer].percent;
    const userDiscount = Math.floor((discountable * percent + 50) / 100); // kuruşa yuvarlama (HALF_UP)
    const billDiscount = Math.floor((subtotal - userDiscount) / BILL_THRESHOLD_CENTS) * BILL_DISCOUNT_CENTS;
    return {
      subtotal: plain(subtotal),
      userDiscountRate: String(percent / 100),
      userDiscount: plain(userDiscount),
      billDiscount: plain(billDiscount),
      total: plain(subtotal - userDiscount - billDiscount),
      totalCents: subtotal - userDiscount - billDiscount,
    };
  }

  function cartItems() {
    return [...cart].map(([id, quantity]) => {
      const p = products.get(id);
      return {
        id, name: p.name, type: p.type, unitPrice: plain(p.cents), quantity,
        lineTotal: plain(p.cents * quantity), phone: p.type === "PHONE",
      };
    });
  }

  function state(message) {
    const { totalCents, ...t } = totals();
    return {
      customer,
      customerName,
      products: [...products.values()].map((p) => ({
        id: p.id, name: p.name, type: p.type, price: plain(p.cents), stock: p.stock,
        available: p.stock - (cart.get(p.id) || 0), inCart: cart.get(p.id) || 0,
      })),
      cart: cartItems(),
      itemCount: [...cart.values()].reduce((sum, quantity) => sum + quantity, 0),
      totals: t,
      canUndo: undoStack.length > 0,
      canRedo: redoStack.length > 0,
      message: message || null,
    };
  }

  /* ---------- Ödeme ---------- */

  // Luhn denetimi; CreditCardPaymentStrategy.isValid ile aynı.
  function validCard(number) {
    if (!/^\d{12,19}$/.test(number)) return false;
    let sum = 0;
    let double = false;
    for (let i = number.length - 1; i >= 0; i--) {
      let digit = number.charCodeAt(i) - 48;
      if (double) {
        digit *= 2;
        if (digit > 9) digit -= 9;
      }
      sum += digit;
      double = !double;
    }
    return sum % 10 === 0;
  }

  function checkout(paymentMethod, cardNumber) {
    if (cart.size === 0) throw new ApiError("Sepetiniz boş.");
    // Kart numarası stok düşülmeden önce doğrulanır; geçersizse hiçbir şey değişmez.
    const digits = (cardNumber || "").replace(/[\s-]/g, "");
    if (paymentMethod === "card" && !validCard(digits)) throw new ApiError("Geçersiz kart numarası.");

    for (const [id, quantity] of cart) {
      const p = products.get(id);
      if (quantity > p.stock) {
        throw new ApiError(`'${p.name}' için stok yetersiz: istenen ${quantity}, mevcut ${p.stock}.`);
      }
    }
    const { totalCents, ...t } = totals();
    const items = cartItems();
    for (const [id, quantity] of cart) products.get(id).stock -= quantity;

    const receipt = {
      invoiceId: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
      date: new Date().toISOString(),
      customerName,
      customerLabel: CUSTOMERS[customer].label,
      items,
      totals: t,
      paymentMessage: paymentMethod === "card"
        ? `**** **** **** ${digits.slice(-4)} numaralı karttan ${money(totalCents)} çekildi.`
        : `Kapıda ödeme seçildi. Kurye teslimatta ${money(totalCents)} tahsil edecek.`,
    };
    cart.clear();
    undoStack = [];
    redoStack = [];
    return receipt;
  }

  /* ---------- Yönlendirme (WebServer.handleApi) ---------- */

  function product(body) {
    const found = products.get(text(body, "productId"));
    if (!found) throw new ApiError("Ürün bulunamadı.");
    return found;
  }

  function handle(method, path, body) {
    if (method === "GET" && path === "/api/state") return state(null);
    if (method !== "POST") return { status: 405, error: "Desteklenmeyen istek." };

    switch (path) {
      case "/api/customer": {
        const kind = text(body, "customer");
        if (!CUSTOMERS[kind]) throw new ApiError("Geçersiz müşteri tipi.");
        customer = kind;
        if (typeof body.name === "string" && body.name.trim()) customerName = body.name.trim();
        return state(null);
      }
      case "/api/cart/add": {
        const p = product(body);
        const quantity = integer(body, "quantity");
        if (quantity <= 0) throw new ApiError("Adet en az 1 olmalı.");
        const available = p.stock - (cart.get(p.id) || 0);
        return state("Sepete eklendi: " + run(addCommand(p, quantity),
          `Stok yetersiz: ${p.name} için en fazla ${available} adet daha eklenebilir.`));
      }
      case "/api/cart/quantity":
        return state(run(quantityCommand(text(body, "productId"), integer(body, "quantity")), "Stok yetersiz."));
      case "/api/cart/remove": {
        const p = products.get(text(body, "productId"));
        if (!p || !cart.has(p.id)) throw new ApiError("Ürün sepette değil.");
        return state("Sepetten çıkarıldı: " + run(removeCommand(p), "Ürün sepette değil."));
      }
      case "/api/undo": {
        const command = undoStack.pop();
        if (!command) return state("Geri alınacak işlem yok.");
        command.undo();
        redoStack.push(command);
        return state("Geri alındı: " + command.describe());
      }
      case "/api/redo": {
        const command = redoStack.pop();
        if (!command || !command.execute()) return state("Yinelenecek işlem yok.");
        undoStack.push(command);
        return state("Yinelendi: " + command.describe());
      }
      case "/api/checkout":
        return checkout(text(body, "payment"), body.cardNumber);
      default:
        return { status: 404, error: "Bulunamadı." };
    }
  }

  function respond(method, path, rawBody) {
    let status = 200;
    let payload;
    try {
      let body = {};
      if (method === "POST" && rawBody) {
        try {
          body = JSON.parse(rawBody) || {};
        } catch (e) {
          throw new ApiError("Geçersiz istek gövdesi.");
        }
      }
      payload = handle(method, path, body);
      if (payload.error) status = payload.status;
    } catch (error) {
      if (!(error instanceof ApiError)) console.error(error);
      status = error instanceof ApiError ? 400 : 500;
      payload = { error: error instanceof ApiError ? error.message : "Sunucu hatası." };
    }
    save();
    return new Response(JSON.stringify(status === 200 ? payload : { error: payload.error }), {
      status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  }

  /* ---------- fetch'i yalnızca /api/* için devral ---------- */

  load();
  const realFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    const url = new URL(typeof input === "string" ? input : input.url, location.href);
    if (url.origin !== location.origin || !url.pathname.startsWith("/api/")) return realFetch(input, init);
    const method = ((init && init.method) || "GET").toUpperCase();
    return Promise.resolve(respond(method, url.pathname, init && init.body));
  };

  // Demo notundaki "sıfırla" bağlantısı: stokları ve sepeti başlangıç haline döndürür.
  document.addEventListener("click", (event) => {
    if (!event.target.closest("[data-demo-reset]")) return;
    event.preventDefault();
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (e) {
      /* yok sayılır */
    }
    location.reload();
  });
})();
