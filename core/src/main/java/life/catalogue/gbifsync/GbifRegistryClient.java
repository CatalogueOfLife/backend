package life.catalogue.gbifsync;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.config.GbifConfig;
import life.catalogue.doi.service.BasicAuthenticator;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Writes to the GBIF registry with the registry account of ChecklistBank, see GbifConfig.username.
 * It is used to register datasets in GBIF from ChecklistBank and to keep their GBIF records up to date.
 * The credentials go into every single request rather than into a client filter, as the jersey client is shared.
 *
 * Every response other than 2xx throws: a 429 a TooManyRequestsException, a 502-504 or an unreachable registry an
 * UnavailableException, which the outbox retries later, and anything else a {@link GbifRegistryException}.
 */
public class GbifRegistryClient {
  private static final Logger LOG = LoggerFactory.getLogger(GbifRegistryClient.class);
  private static final TypeReference<List<Endpoint>> ENDPOINTS = new TypeReference<>() {};
  private static final TypeReference<List<Identifier>> IDENTIFIERS = new TypeReference<>() {};
  private final WebTarget datasets;
  @Nullable
  private final String auth;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Endpoint(Integer key, String type, String url) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Identifier(Integer key, String type, String identifier) {}

  public GbifRegistryClient(Client client, GbifConfig cfg) {
    this.datasets = client.target(UriBuilder.fromUri(cfg.api).path("dataset"));
    this.auth = cfg.username != null && cfg.password != null ? BasicAuthenticator.basicAuthentication(cfg.username, cfg.password) : null;
  }

  public boolean hasCredentials() {
    return auth != null;
  }

  /**
   * @param dataset the GBIF dataset as JSON, see the GBIF registry API
   * @return the new GBIF dataset key
   */
  public UUID createDataset(ObjectNode dataset) {
    String body = send("create dataset", () -> request(datasets).post(Entity.json(json(dataset))), false);
    return UUID.fromString(body.replace("\"", "").trim());
  }

  /**
   * @return the GBIF dataset as JSON or null if it does not exist
   */
  @Nullable
  public ObjectNode getDataset(UUID key) {
    String body = send("get dataset " + key, () -> request(path(key)).get(), true);
    return body == null ? null : parse(body, ObjectNode.class);
  }

  /**
   * Updates a GBIF dataset. GBIF replaces the whole record, so pass the dataset as it was read and changed.
   */
  public void updateDataset(ObjectNode dataset) {
    UUID key = UUID.fromString(dataset.path("key").asText());
    send("update dataset " + key, () -> request(path(key)).put(Entity.json(json(dataset))), false);
  }

  /**
   * @return false if the dataset did not exist
   */
  public boolean deleteDataset(UUID key) {
    return send("delete dataset " + key, () -> request(path(key)).delete(), true) != null;
  }

  /**
   * Uploads a metadata document, e.g. EML, which GBIF stores and updates the dataset from.
   * GBIF keeps one document per type, and an EML document takes precedence over all others.
   */
  public void insertMetadata(UUID key, byte[] document) {
    send("insert metadata document into " + key,
      () -> request(path(key, "document")).post(Entity.entity(document, MediaType.APPLICATION_XML_TYPE)), false);
  }

  public List<Endpoint> listEndpoints(UUID key) {
    return parse(send("list endpoints of " + key, () -> request(path(key, "endpoint")).get(), false), ENDPOINTS);
  }

  public void addEndpoint(UUID key, String type, URI url) {
    ObjectNode e = ApiModule.MAPPER.createObjectNode();
    e.put("type", type);
    e.put("url", url.toString());
    send("add " + type + " endpoint to " + key, () -> request(path(key, "endpoint")).post(Entity.json(json(e))), false);
  }

  public void deleteEndpoint(UUID key, int endpointKey) {
    send("delete endpoint " + endpointKey + " of " + key, () -> request(path(key, "endpoint", endpointKey)).delete(), true);
  }

  public List<Identifier> listIdentifiers(UUID key) {
    return parse(send("list identifiers of " + key, () -> request(path(key, "identifier")).get(), false), IDENTIFIERS);
  }

  public void addIdentifier(UUID key, String type, String identifier) {
    ObjectNode id = ApiModule.MAPPER.createObjectNode();
    id.put("type", type);
    id.put("identifier", identifier);
    send("add " + type + " identifier to " + key, () -> request(path(key, "identifier")).post(Entity.json(json(id))), false);
  }

  public void deleteIdentifier(UUID key, int identifierKey) {
    send("delete identifier " + identifierKey + " of " + key, () -> request(path(key, "identifier", identifierKey)).delete(), true);
  }

  /**
   * Asks GBIF to crawl the dataset.
   */
  public void crawl(UUID key) {
    send("crawl " + key, () -> request(path(key, "crawl")).post(Entity.json("")), false);
  }

  private WebTarget path(UUID key, Object... sub) {
    WebTarget t = datasets.path(key.toString());
    for (Object s : sub) {
      t = t.path(s.toString());
    }
    return t;
  }

  private Invocation.Builder request(WebTarget target) {
    if (auth == null) {
      throw new IllegalStateException("No GBIF registry credentials configured");
    }
    return target.request(MediaType.APPLICATION_JSON_TYPE)
      .header(HttpHeaders.AUTHORIZATION, auth);
  }

  /**
   * @param missingIsNull if true a 404 returns null instead of throwing
   * @return the response body, an empty string if there is none
   */
  private String send(String action, Supplier<Response> call, boolean missingIsNull) {
    LOG.debug("GBIF registry: {}", action);
    try (Response resp = call.get()) {
      int status = resp.getStatus();
      String body = resp.hasEntity() ? resp.readEntity(String.class) : "";
      if (status >= 200 && status < 300) {
        return body;
      } else if (status == 404 && missingIsNull) {
        return null;
      } else if (status == 429) {
        throw new TooManyRequestsException("GBIF registry refused to " + action + ": too many requests");
      } else if (status == 502 || status == 503 || status == 504) {
        throw new UnavailableException("GBIF registry unavailable to " + action + ": HTTP " + status);
      }
      throw new GbifRegistryException(status, "GBIF registry failed to " + action + ": HTTP " + status + " " + body);
    } catch (ProcessingException e) {
      throw new UnavailableException("GBIF registry not reachable to " + action + ": " + e.getMessage());
    }
  }

  private static String json(Object obj) {
    try {
      return ApiModule.MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialise GBIF registry request", e);
    }
  }

  private static <T> T parse(String body, Class<T> clazz) {
    try {
      return ApiModule.MAPPER.readValue(body, clazz);
    } catch (IOException e) {
      throw new GbifRegistryException(0, "Cannot read GBIF registry response: " + e.getMessage());
    }
  }

  private static <T> T parse(String body, TypeReference<T> type) {
    try {
      return ApiModule.MAPPER.readValue(body, type);
    } catch (IOException e) {
      throw new GbifRegistryException(0, "Cannot read GBIF registry response: " + e.getMessage());
    }
  }

  /**
   * A GBIF registry response other than 2xx, a 404 where that means missing, 429 or 502-504.
   */
  public static class GbifRegistryException extends RuntimeException {
    private final int status;

    public GbifRegistryException(int status, String message) {
      super(message);
      this.status = status;
    }

    /**
     * @return the HTTP status, 0 if the response could not be read
     */
    public int getStatus() {
      return status;
    }

    /**
     * @return true for a client error, which retrying the same request cannot fix
     */
    public boolean isClientError() {
      return status >= 400 && status < 500;
    }
  }
}
