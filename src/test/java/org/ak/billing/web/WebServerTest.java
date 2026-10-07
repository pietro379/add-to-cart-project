package org.ak.billing.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.ak.billing.daos.impls.FileStoreDao;
import org.ak.billing.services.impls.MyCartService;
import org.ak.billing.services.impls.MyInvoiceService;
import org.ak.billing.services.impls.MyStoreDBService;
import org.ak.billing.strategies.impls.MyCartLoadingStrategy;
import org.ak.billing.strategies.impls.MyInvoiceGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sunucuyu boş bir portta başlatıp API'yi gerçek HTTP istekleriyle dener. */
class WebServerTest {

    @TempDir
    Path tempDir;

    private final Gson gson = new Gson();
    private WebServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        MyStoreDBService store = new MyStoreDBService(new FileStoreDao(tempDir.resolve("inventory.csv")));
        MyCartService cartService = new MyCartService(store, new MyCartLoadingStrategy());
        MyInvoiceGenerator generator = new MyInvoiceGenerator();
        server = new WebServer(() -> new ShopSession(store, cartService, new MyInvoiceService(generator), generator));
        baseUrl = "http://localhost:" + server.start(0);
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> send(String path, String jsonBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (jsonBody != null) {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject json(HttpResponse<String> response) {
        return gson.fromJson(response.body(), JsonObject.class);
    }

    private String productId(JsonObject state, String name) {
        JsonArray products = state.getAsJsonArray("products");
        for (var p : products) {
            if (p.getAsJsonObject().get("name").getAsString().equals(name)) {
                return p.getAsJsonObject().get("id").getAsString();
            }
        }
        throw new AssertionError(name + " bulunamadı");
    }

    @Test
    @DisplayName("Arayüz dosyaları sunulur, sınıf yolunda gezinme engellenir")
    void servesStaticFiles() throws Exception {
        assertEquals(200, send("/", null).statusCode());
        assertEquals(200, send("/app.js", null).statusCode());
        assertEquals(404, send("/../org/ak/billing/Main.class", null).statusCode());
    }

    @Test
    @DisplayName("Gold üye sepeti: canlı indirim dökümü ve kapıda ödeme")
    void goldCheckout() throws Exception {
        JsonObject state = json(send("/api/state", null));
        String jacket = productId(state, "BLUE MEN JACKET");

        send("/api/customer", "{\"customer\":\"GOLD\"}");
        state = json(send("/api/cart/add", "{\"productId\":\"" + jacket + "\",\"quantity\":2}"));

        JsonObject totals = state.getAsJsonObject("totals");
        assertEquals("39.98", totals.get("subtotal").getAsString());
        assertEquals("11.99", totals.get("userDiscount").getAsString());
        assertEquals("27.99", totals.get("total").getAsString());

        JsonObject receipt = json(send("/api/checkout", "{\"payment\":\"cod\"}"));
        assertEquals("27.99", receipt.getAsJsonObject("totals").get("total").getAsString());

        state = json(send("/api/state", null));
        assertEquals(0, state.get("itemCount").getAsInt());
        assertEquals(18, state.getAsJsonArray("products").get(0).getAsJsonObject().get("stock").getAsInt());
    }

    @Test
    @DisplayName("Geçersiz kart stokları değiştirmez; adet güncelleme geri alınabilir")
    void invalidCardAndUndo() throws Exception {
        String phone = productId(json(send("/api/state", null)), "SAMSUNG S3 MINI");
        send("/api/cart/add", "{\"productId\":\"" + phone + "\",\"quantity\":1}");
        send("/api/cart/quantity", "{\"productId\":\"" + phone + "\",\"quantity\":3}");

        HttpResponse<String> failed = send("/api/checkout", "{\"payment\":\"card\",\"cardNumber\":\"1234\"}");
        assertEquals(400, failed.statusCode());
        assertTrue(failed.body().contains("Geçersiz kart numarası"), failed.body());

        JsonObject state = json(send("/api/undo", "{}"));
        assertEquals(1, state.get("itemCount").getAsInt());
        assertEquals(20, state.getAsJsonArray("products").get(3).getAsJsonObject().get("stock").getAsInt());
    }

    @Test
    @DisplayName("Stoku aşan ekleme anlaşılır bir hata döner")
    void addingBeyondStock() throws Exception {
        String phone = productId(json(send("/api/state", null)), "SAMSUNG S3 MINI");

        HttpResponse<String> response = send("/api/cart/add", "{\"productId\":\"" + phone + "\",\"quantity\":21}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("en fazla 20 adet"), response.body());
    }
}
