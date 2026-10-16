package com.wsnsa.payment.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Гадаад API-г (provider, PosAPI, merchant webhook) дуурайх энгийн HTTP server. */
public class StubServer implements AutoCloseable {

    public record Request(String method, String path, String query, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            for (var e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) return e.getValue().get(0);
            }
            return null;
        }
    }

    public record Response(int status, String body) {
        public static Response json(String body) {
            return new Response(200, body);
        }
    }

    private final HttpServer server;
    private final Map<String, Function<Request, Response>> routes = new ConcurrentHashMap<>();
    public final List<Request> requests = new CopyOnWriteArrayList<>();

    public StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public StubServer on(String method, String path, Function<Request, Response> handler) {
        routes.put(method + " " + path, handler);
        return this;
    }

    public List<Request> requests(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Request req = new Request(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestURI().getQuery(),
                Map.copyOf(ex.getRequestHeaders()), body);
        requests.add(req);
        Function<Request, Response> h = routes.get(req.method() + " " + req.path());
        Response res = h == null ? new Response(404, "{\"message\":\"no route\"}") : h.apply(req);
        byte[] bytes = res.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(res.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
