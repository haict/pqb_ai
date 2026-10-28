package com.company.erp.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * AI Service giả lập cho test tích hợp: lắng nghe cổng cấu hình ở {@code application-test.yml}, trả phản hồi đặt trước
 * và ghi lại header nhận được. Không cần chạy Python.
 */
final class AiServiceStub implements AutoCloseable {

    static final int PORT = 18090;
    static final String API_KEY = "test-ai-service-key-0001";

    record Received(String apiKey, String requestId, String contentType, int bodyLength, String body) {
    }

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile Duration delay = Duration.ZERO;
    private volatile boolean ready = true;
    private volatile int chatStatus = 200;
    private volatile String chatBody = "{}";

    AiServiceStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/internal/v1/health", exchange -> respond(exchange, 200,
                "{\"status\":\"UP\",\"ocr\":{\"ready\":" + ready + "},\"chat\":{\"ready\":" + ready + "}}"));
        server.createContext("/internal/v1/ocr/extract", exchange -> handle(exchange, status, body));
        server.createContext("/internal/v1/chat", exchange -> handle(exchange, chatStatus, chatBody));
        server.start();
    }

    void reply(int newStatus, String newBody) {
        this.status = newStatus;
        this.body = newBody;
        this.delay = Duration.ZERO;
    }

    void chatReply(int newStatus, String newBody) {
        this.chatStatus = newStatus;
        this.chatBody = newBody;
        this.delay = Duration.ZERO;
    }

    void delay(Duration newDelay) {
        this.delay = newDelay;
    }

    void ready(boolean newReady) {
        this.ready = newReady;
    }

    List<Received> received() {
        return received;
    }

    private void handle(HttpExchange exchange, int code, String response) throws IOException {
        byte[] request = exchange.getRequestBody().readAllBytes();
        // Multipart chứa byte nhị phân: ISO-8859-1 giữ nguyên từng byte; JSON chat đọc theo UTF-8.
        String contentType = String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type"));
        boolean utf8 = contentType.startsWith("application/json");
        received.add(new Received(exchange.getRequestHeaders().getFirst("X-AI-Service-Key"),
                exchange.getRequestHeaders().getFirst("X-Request-Id"),
                exchange.getRequestHeaders().getFirst("Content-Type"), request.length,
                new String(request, utf8 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1)));
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        respond(exchange, code, response);
    }

    private static void respond(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException ignored) {
            // Client đã đóng kết nối (test quá thời gian chờ).
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
