package org.ak.billing.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.ak.billing.exceptions.InventoryShortageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * JDK'nın yerleşik HTTP sunucusuyla JSON API ve statik arayüz dosyalarını sunar.
 * Her tarayıcı bir çerezle kendi {@link ShopSession}'ına bağlanır.
 */
public class WebServer {
    private static final String COOKIE = "atc_session";
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml");

    private final Gson gson = new Gson();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ShopSession> sessions = new ConcurrentHashMap<>();
    private final Supplier<ShopSession> sessionFactory;
    private HttpServer server;

    public WebServer(Supplier<ShopSession> sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /** Sunucuyu yalnızca bu makineden erişilecek şekilde başlatır; 0 verilirse boş bir port seçilir. */
    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        // Varsayılan yürütücü istekleri tek thread'de işler; CSV deposuna eşzamanlı yazım olmaz.
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try (exchange) {
            ShopSession session = session(exchange);
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (method.equals("GET") && path.equals("/api/state")) {
                json(exchange, 200, session.state(null));
                return;
            }
            if (!method.equals("POST")) {
                error(exchange, 405, "Desteklenmeyen istek.");
                return;
            }

            JsonObject body = readBody(exchange);
            try {
                Object response = switch (path) {
                    case "/api/customer" -> {
                        session.setCustomer(ShopSession.CustomerKind.valueOf(text(body, "customer")),
                                optionalText(body, "name").orElse(null));
                        yield session.state(null);
                    }
                    case "/api/cart/add" -> session.state("Sepete eklendi: "
                            + session.add(uuid(body), integer(body, "quantity")));
                    case "/api/cart/quantity" -> session.state(session.setQuantity(uuid(body), integer(body, "quantity")));
                    case "/api/cart/remove" -> session.state("Sepetten çıkarıldı: " + session.remove(uuid(body)));
                    case "/api/undo" -> session.state(session.undo());
                    case "/api/redo" -> session.state(session.redo());
                    case "/api/checkout" -> session.checkout(text(body, "payment"),
                            optionalText(body, "cardNumber").orElse(""));
                    default -> null;
                };
                if (response == null) {
                    error(exchange, 404, "Bulunamadı.");
                } else {
                    json(exchange, 200, response);
                }
            } catch (IllegalArgumentException | InventoryShortageException e) {
                error(exchange, 400, e.getMessage());
            } catch (RuntimeException e) {
                e.printStackTrace();
                error(exchange, 500, "Sunucu hatası.");
            }
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }
            // Yalnızca düz dosya adları; "../" ile sınıf yolunda gezinmeyi engeller.
            if (!path.matches("/[a-z0-9-]+\\.(html|css|js|svg)")) {
                send(exchange, 404, "text/plain; charset=utf-8", "Bulunamadı".getBytes(StandardCharsets.UTF_8));
                return;
            }
            try (InputStream in = getClass().getResourceAsStream("/web" + path)) {
                if (in == null) {
                    send(exchange, 404, "text/plain; charset=utf-8", "Bulunamadı".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                String extension = path.substring(path.lastIndexOf('.') + 1);
                send(exchange, 200, CONTENT_TYPES.get(extension), in.readAllBytes());
            }
        }
    }

    private ShopSession session(HttpExchange exchange) {
        String cookie = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Cookie")).orElse("");
        for (String part : cookie.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(COOKIE) && sessions.containsKey(kv[1])) {
                return sessions.get(kv[1]);
            }
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        sessions.put(id, sessionFactory.get());
        exchange.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + id + "; Path=/; HttpOnly; SameSite=Strict");
        return sessions.get(id);
    }

    private JsonObject readBody(HttpExchange exchange) {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonObject body = gson.fromJson(reader, JsonObject.class);
            return body == null ? new JsonObject() : body;
        } catch (IOException | JsonParseException e) {
            throw new IllegalArgumentException("Geçersiz istek gövdesi.");
        }
    }

    private static String text(JsonObject body, String field) {
        return optionalText(body, field).orElseThrow(() -> new IllegalArgumentException(field + " alanı gerekli."));
    }

    private static Optional<String> optionalText(JsonObject body, String field) {
        return body.has(field) && !body.get(field).isJsonNull()
                ? Optional.of(body.get(field).getAsString())
                : Optional.empty();
    }

    private static int integer(JsonObject body, String field) {
        try {
            return Integer.parseInt(text(body, field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " bir sayı olmalı.");
        }
    }

    private static UUID uuid(JsonObject body) {
        return UUID.fromString(text(body, "productId"));
    }

    private void json(HttpExchange exchange, int status, Object payload) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    private void error(HttpExchange exchange, int status, String message) throws IOException {
        json(exchange, status, Map.of("error", message == null ? "Beklenmeyen hata." : message));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
