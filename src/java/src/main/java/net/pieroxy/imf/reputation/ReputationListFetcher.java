package net.pieroxy.imf.reputation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Récupère le contenu brut d'une liste : {@code file://} pour un fichier local, {@code http(s)://}
 * sinon. Rien d'autre que le contenu de la liste elle-même ne part jamais sur le réseau — aucune
 * IP/domaine de message n'est envoyé, contrairement à une API de réputation interrogée en direct.
 */
final class ReputationListFetcher {
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  private ReputationListFetcher() {}

  static String fetch(String url) throws IOException, InterruptedException {
    URI uri = URI.create(url);
    if ("file".equalsIgnoreCase(uri.getScheme())) {
      return Files.readString(Path.of(uri), StandardCharsets.UTF_8);
    }
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IOException("Unexpected HTTP status " + response.statusCode() + " fetching " + url);
    }
    return response.body();
  }
}
