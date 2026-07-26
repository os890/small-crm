/*
 * Copyright 2026 the Small CRM authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.os890.smallcrm.google;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A stand-in for Google, so the integration can be tested without an account.
 *
 * <p>Built on the JDK's own HTTP server rather than a stubbing library: it is a few dozen lines,
 * adds no dependency to a build that is deliberately lean, and being ordinary Java it can answer
 * differently on the second call, which is exactly what testing token refresh and expired sync
 * tokens needs.
 *
 * <p>This proves the wiring, the mapping and the error handling. It cannot prove that Google's
 * real responses look like these — only a run against real credentials does that, and that is
 * recorded as the remaining gap rather than pretended away.
 */
public final class GoogleStub implements AutoCloseable {

  /** Every request the code under test made, so a test can assert on what was sent. */
  public final List<Recorded> requests = new CopyOnWriteArrayList<>();

  private final HttpServer server;
  private final Map<String, Function<Recorded, Reply>> handlers = new ConcurrentHashMap<>();

  /** One request as the stub saw it. */
  public record Recorded(
      String method, String path, String query, String body, String authorization) {

    public boolean bodyHas(String fragment) {
      return body != null && body.contains(fragment);
    }
  }

  /** What to answer with. */
  public record Reply(int status, String body) {

    public static Reply ok(String body) {
      return new Reply(200, body);
    }

    public static Reply status(int status, String body) {
      return new Reply(status, body);
    }
  }

  public GoogleStub() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::dispatch);
    server.start();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Registers the answer for a path. The handler sees the request, so it can vary. */
  public GoogleStub on(String path, Function<Recorded, Reply> handler) {
    handlers.put(path, handler);
    return this;
  }

  public GoogleStub onGet(String path, String body) {
    return on(path, request -> Reply.ok(body));
  }

  /** The requests made to one path, in order. */
  public List<Recorded> requestsTo(String path) {
    List<Recorded> matching = new ArrayList<>();
    for (Recorded request : requests) {
      if (request.path().equals(path)) {
        matching.add(request);
      }
    }
    return matching;
  }

  /**
   * An id token shaped like Google's.
   *
   * <p>Unsigned, because the code deliberately does not verify the signature: the real one
   * arrives over a direct TLS connection to Google's token endpoint, which OpenID Connect
   * accepts in place of a signature check.
   */
  public static String idToken(String subject, String email, boolean verified) {
    String header = encode("{\"alg\":\"none\",\"typ\":\"JWT\"}");
    String payload =
        encode(
            ("{\"sub\":\"%s\",\"email\":\"%s\",\"email_verified\":%s,"
                    + "\"iss\":\"accounts.google.com\"}")
                .formatted(subject, email, verified));
    return header + "." + payload + ".";
  }

  private static String encode(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private void dispatch(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Recorded request =
        new Recorded(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            exchange.getRequestURI().getQuery(),
            body,
            exchange.getRequestHeaders().getFirst("Authorization"));
    requests.add(request);

    Function<Recorded, Reply> handler = handlers.get(request.path());
    Reply reply =
        handler == null
            ? Reply.status(404, "{\"error\":\"no stub for " + request.path() + "\"}")
            : handler.apply(request);

    byte[] payload =
        reply.body() == null ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(reply.status(), payload.length);
    exchange.getResponseBody().write(payload);
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
