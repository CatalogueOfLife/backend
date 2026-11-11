package life.catalogue.gbifsync;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.config.GbifConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny in memory stand in for the dataset part of the GBIF registry API, enough to test what ChecklistBank writes.
 * Requests without the expected basic auth header get a 401.
 */
public class FakeGbifRegistry implements AutoCloseable {
  public static final String USER = "clb";
  public static final String PASSWORD = "secret";

  private final HttpServer server;
  public final Map<UUID, ObjectNode> datasets = new ConcurrentHashMap<>();
  public final Map<UUID, List<ObjectNode>> endpoints = new ConcurrentHashMap<>();
  public final Map<UUID, List<ObjectNode>> identifiers = new ConcurrentHashMap<>();
  public final Map<UUID, Integer> crawls = new ConcurrentHashMap<>();
  public final Set<UUID> deleted = ConcurrentHashMap.newKeySet();
  private final AtomicInteger subKeys = new AtomicInteger(100);
  // the status to answer the next request with instead of processing it
  public volatile Integer failNext;
  // the key to give the next dataset created, a random one if null
  public volatile UUID nextKey;

  public FakeGbifRegistry() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/dataset", this::handle);
    server.start();
  }

  public GbifConfig config() {
    GbifConfig cfg = new GbifConfig();
    cfg.api = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
    cfg.username = USER;
    cfg.password = PASSWORD;
    cfg.installationKey = UUID.fromString("3298f964-ee79-44d3-9ac3-4236aa62c5f9");
    return cfg;
  }

  public int crawls(UUID key) {
    return crawls.getOrDefault(key, 0);
  }

  public List<ObjectNode> endpoints(UUID key) {
    return endpoints.getOrDefault(key, List.of());
  }

  public List<ObjectNode> identifiers(UUID key) {
    return identifiers.getOrDefault(key, List.of());
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange ex) throws IOException {
    try {
      if (failNext != null) {
        int status = failNext;
        failNext = null;
        send(ex, status, "{\"error\":\"forced\"}");
        return;
      }
      String expected = "Basic " + Base64.getEncoder().encodeToString((USER + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
      if (!expected.equals(ex.getRequestHeaders().getFirst("Authorization"))) {
        send(ex, 401, "");
        return;
      }
      // path below /v1/dataset
      String[] parts = Arrays.stream(ex.getRequestURI().getPath().substring("/v1/dataset".length()).split("/"))
        .filter(p -> !p.isEmpty())
        .toArray(String[]::new);
      String method = ex.getRequestMethod();
      if (parts.length == 0 && method.equals("POST")) {
        ObjectNode d = (ObjectNode) ApiModule.MAPPER.readTree(body(ex));
        UUID key = nextKey == null ? UUID.randomUUID() : nextKey;
        nextKey = null;
        d.put("key", key.toString());
        datasets.put(key, d);
        send(ex, 201, "\"" + key + "\"");
        return;
      }
      UUID key = UUID.fromString(parts[0]);
      if (!datasets.containsKey(key)) {
        send(ex, 404, "");
        return;
      }
      if (parts.length == 1) {
        switch (method) {
          case "GET" -> send(ex, 200, ApiModule.MAPPER.writeValueAsString(datasets.get(key)));
          case "PUT" -> {
            datasets.put(key, (ObjectNode) ApiModule.MAPPER.readTree(body(ex)));
            send(ex, 204, null);
          }
          case "DELETE" -> {
            datasets.remove(key);
            deleted.add(key);
            send(ex, 204, null);
          }
          default -> send(ex, 405, "");
        }
      } else if (parts[1].equals("crawl")) {
        crawls.merge(key, 1, Integer::sum);
        send(ex, 204, null);
      } else {
        var store = parts[1].equals("endpoint") ? endpoints : identifiers;
        var list = store.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        if (parts.length == 2 && method.equals("GET")) {
          ArrayNode arr = ApiModule.MAPPER.createArrayNode();
          list.forEach(arr::add);
          send(ex, 200, ApiModule.MAPPER.writeValueAsString(arr));
        } else if (parts.length == 2 && method.equals("POST")) {
          ObjectNode e = (ObjectNode) ApiModule.MAPPER.readTree(body(ex));
          int subKey = subKeys.incrementAndGet();
          e.put("key", subKey);
          list.add(e);
          send(ex, 201, String.valueOf(subKey));
        } else if (parts.length == 3 && method.equals("DELETE")) {
          int subKey = Integer.parseInt(parts[2]);
          boolean removed = list.removeIf(e -> e.path("key").asInt() == subKey);
          send(ex, removed ? 204 : 404, null);
        } else {
          send(ex, 405, "");
        }
      }
    } catch (RuntimeException e) {
      send(ex, 500, e.getMessage());
    }
  }

  private static String body(HttpExchange ex) throws IOException {
    try (InputStream in = ex.getRequestBody()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void send(HttpExchange ex, int status, String body) throws IOException {
    if (body == null || body.isEmpty()) {
      ex.sendResponseHeaders(status, -1);
    } else {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "application/json");
      ex.sendResponseHeaders(status, bytes.length);
      ex.getResponseBody().write(bytes);
    }
    ex.close();
  }
}
