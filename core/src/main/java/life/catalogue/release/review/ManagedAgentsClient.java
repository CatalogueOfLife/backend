package life.catalogue.release.review;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

/**
 * A deliberately small client for the handful of Anthropic Managed Agents calls the release review needs.
 *
 * No SDK: the managed agents surface is beta, we use six endpoints, and pinning a large client library to a
 * moving beta is a worse trade than forty lines of HTTP. Every response is read as a Jackson tree and only the
 * fields we actually need are pulled out, so new or renamed fields elsewhere in a response can never break a run.
 *
 * The api key is a header value and nothing else: it is never put in a log line, a message or an exception.
 *
 * See https://platform.claude.com/docs/en/managed-agents/
 */
public class ManagedAgentsClient {
  private static final Logger LOG = LoggerFactory.getLogger(ManagedAgentsClient.class);
  private static final ObjectMapper OM = new ObjectMapper();
  static final String ANTHROPIC_VERSION = "2023-06-01";
  static final String ANTHROPIC_BETA = "managed-agents-2026-04-01";

  private final AiReviewConfig cfg;
  private final CloseableHttpClient http;

  public ManagedAgentsClient(AiReviewConfig cfg, CloseableHttpClient http) {
    this.cfg = cfg;
    this.http = http;
  }

  /**
   * A session as the API currently reports it. Anything beyond the status and the cost is ignored.
   */
  public static class SessionState {
    public final String status;
    public final @Nullable String listCost;

    SessionState(String status, String listCost) {
      this.status = status;
      this.listCost = listCost;
    }

    /**
     * Only an explicitly running or queued session keeps the job polling. Every other value - including one
     * this code has never seen - ends the wait, because a client that treats an unknown status as "keep going"
     * polls until the timeout for nothing.
     */
    public boolean isTerminal() {
      if (status == null) return false;
      switch (status.toLowerCase()) {
        case "running":
        case "queued":
        case "pending":
        case "starting":
        case "in_progress":
          return false;
        default:
          return true;
      }
    }

    @Override
    public String toString() {
      return status + (listCost == null ? "" : " (" + listCost + ")");
    }
  }

  /**
   * A file the API lists for a session.
   */
  public static class FileInfo {
    public final String id;
    public final @Nullable String filename;

    FileInfo(String id, String filename) {
      this.id = id;
      this.filename = filename;
    }

    @Override
    public String toString() {
      return filename + " [" + id + "]";
    }
  }

  /**
   * Uploads a file to the workspace so it can be mounted into a session.
   *
   * @return the new file id
   */
  public String uploadFile(String filename, byte[] content, ContentType type) throws IOException {
    HttpPost post = new HttpPost(cfg.resolve("/v1/files"));
    post.setEntity(MultipartEntityBuilder.create()
      .addBinaryBody("file", content, type, filename)
      .build());
    JsonNode resp = execute(post, "upload " + filename);
    String id = text(resp, "id");
    if (id == null) {
      throw new IOException("Managed agents file upload returned no id");
    }
    LOG.info("Uploaded {} ({} bytes) as managed agents file {}", filename, content.length, id);
    return id;
  }

  /**
   * Rotates the secret value of the ChecklistBank credential in the vault.
   *
   * The credential is injected as an environment variable and used as a Bearer token by the agent. It must NOT
   * be basic auth: the vault only substitutes the real secret at egress, so anything the sandbox does to the
   * placeholder itself - base64 encoding it for basic auth above all - destroys it before it ever leaves.
   *
   * @param secretValue the freshly minted JWT. Never logged.
   */
  public void rotateCredential(String secretValue) throws IOException {
    ObjectNode auth = OM.createObjectNode();
    auth.put("type", "environment_variable");
    auth.put("secret_value", secretValue);
    ObjectNode body = OM.createObjectNode();
    body.set("auth", auth);

    HttpPost post = new HttpPost(cfg.resolve("/v1/vaults/" + cfg.vaultId + "/credentials/" + cfg.credentialId));
    post.setEntity(json(body));
    execute(post, "rotate credential " + cfg.credentialId);
    LOG.info("Rotated the review bot credential {} in vault {}", cfg.credentialId, cfg.vaultId);
  }

  /**
   * An uploaded file and where to mount it, e.g. /sector-metrics.json. The agent reads it under
   * /mnt/session/uploads/ followed by the mount path.
   */
  public record Mount(String fileId, String mountPath) {}

  /**
   * Starts a session on the configured agent and environment.
   *
   * @param task   the rendered review task, sent as the first user message
   * @param mounts the uploaded files to mount into the session
   * @return the new session id
   */
  public String createSession(String task, List<Mount> mounts) throws IOException {
    ObjectNode body = OM.createObjectNode();
    body.put("agent", cfg.agentId);
    body.put("environment_id", cfg.environmentId);
    body.putArray("vault_ids").add(cfg.vaultId);

    ObjectNode cost = OM.createObjectNode();
    cost.put("amount", cfg.maxCostCents);
    cost.put("currency", "USD");
    ObjectNode budget = OM.createObjectNode();
    budget.put("type", "limit");
    budget.set("max_list_cost", cost);
    body.set("budget", budget);

    if (!mounts.isEmpty()) {
      var resources = body.putArray("resources");
      for (var m : mounts) {
        ObjectNode res = OM.createObjectNode();
        res.put("type", "file");
        res.put("file_id", m.fileId());
        res.put("mount_path", m.mountPath());
        resources.add(res);
      }
    }

    ObjectNode text = OM.createObjectNode();
    text.put("type", "text");
    text.put("text", task);
    ObjectNode msg = OM.createObjectNode();
    msg.put("type", "user.message");
    msg.putArray("content").add(text);
    body.putArray("initial_events").add(msg);

    HttpPost post = new HttpPost(cfg.resolve("/v1/sessions"));
    post.setEntity(json(body));
    JsonNode resp = execute(post, "create session");
    String id = text(resp, "id");
    if (id == null) {
      throw new IOException("Managed agents session creation returned no id");
    }
    LOG.info("Started managed agents session {} on agent {}", id, cfg.agentId);
    return id;
  }

  public SessionState getSession(String sessionId) throws IOException {
    HttpGet get = new HttpGet(cfg.resolve("/v1/sessions/" + sessionId));
    JsonNode resp = execute(get, "get session " + sessionId);
    return new SessionState(text(resp, "status"), cost(resp));
  }

  /**
   * Lists the files a session produced, i.e. everything it wrote to /mnt/session/outputs/.
   * Can legitimately come back empty for a few seconds after a session went idle - callers retry.
   */
  public List<FileInfo> listSessionFiles(String sessionId) throws IOException {
    HttpGet get = new HttpGet(cfg.resolve("/v1/files?scope_id=" + sessionId));
    JsonNode resp = execute(get, "list files of session " + sessionId);
    JsonNode arr = resp.path("data");
    if (!arr.isArray()) {
      arr = resp.isArray() ? resp : OM.createArrayNode();
    }
    List<FileInfo> files = new ArrayList<>();
    for (JsonNode n : arr) {
      String id = text(n, "id");
      if (id != null) {
        String name = text(n, "filename");
        files.add(new FileInfo(id, name != null ? name : text(n, "name")));
      }
    }
    return files;
  }

  public byte[] downloadFile(String fileId) throws IOException {
    HttpGet get = new HttpGet(cfg.resolve("/v1/files/" + fileId + "/content"));
    addHeaders(get);
    return http.execute(get, (HttpClientResponseHandler<byte[]>) resp -> {
      HttpEntity e = resp.getEntity();
      if (resp.getCode() / 100 != 2) {
        throw new IOException("Managed agents download of file " + fileId + " failed with HTTP " + resp.getCode()
          + ": " + shorten(e));
      }
      if (e == null) {
        return new byte[0];
      }
      try (InputStream in = e.getContent(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        in.transferTo(out);
        return out.toByteArray();
      }
    });
  }

  private StringEntity json(JsonNode body) throws IOException {
    return new StringEntity(OM.writeValueAsString(body), ContentType.APPLICATION_JSON);
  }

  private void addHeaders(HttpUriRequestBase req) {
    req.addHeader("x-api-key", cfg.apiKey);
    req.addHeader("anthropic-version", ANTHROPIC_VERSION);
    req.addHeader("anthropic-beta", ANTHROPIC_BETA);
  }

  private JsonNode execute(HttpUriRequestBase req, String what) throws IOException {
    addHeaders(req);
    LOG.debug("Managed agents {} {}", req.getMethod(), req.getPath());
    return http.execute(req, (HttpClientResponseHandler<JsonNode>) resp -> {
      String body = resp.getEntity() == null ? "" : EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
      if (resp.getCode() / 100 != 2) {
        // the api key is a header and never appears in a response body
        throw new IOException("Managed agents failed to " + what + ": HTTP " + resp.getCode() + " " + shorten(body));
      }
      return body.isBlank() ? OM.createObjectNode() : OM.readTree(body);
    });
  }

  /**
   * The cost of a session, wherever the API happens to put it. A shape we do not know simply comes back as null
   * instead of failing a finished review.
   *
   * The documented {@code usage.list_cost} is {@code {amount, currency}} with the amount an integer string in minor
   * units, exactly like the {@code max_list_cost} of the budget we send - so {"605", "USD"} is 6.05 USD, not 605.
   */
  @VisibleForTesting
  static @Nullable String cost(JsonNode session) {
    for (String field : new String[]{"list_cost", "listCost", "cost", "total_cost"}) {
      JsonNode n = session.path(field);
      if (n.isMissingNode() || n.isNull()) {
        JsonNode usage = session.path("usage").path(field);
        n = usage;
      }
      if (n.isValueNode()) {
        return n.asText();
      }
      if (n.isObject()) {
        String amount = text(n, "amount");
        if (amount != null) {
          String currency = text(n, "currency");
          return currency == null ? amount : majorUnits(amount, currency) + " " + currency;
        }
      }
    }
    return null;
  }

  /**
   * Converts an integer amount of minor units, e.g. cents, into the major unit of its currency.
   * Anything that is not an integer or not a known currency is returned verbatim rather than guessed at.
   */
  private static String majorUnits(String minorUnits, String currency) {
    try {
      int digits = Currency.getInstance(currency).getDefaultFractionDigits();
      return digits < 0 ? minorUnits : new BigDecimal(new BigInteger(minorUnits), digits).toPlainString();
    } catch (IllegalArgumentException e) {
      // also covers NumberFormatException
      return minorUnits;
    }
  }

  private static @Nullable String text(JsonNode node, String field) {
    JsonNode n = node == null ? null : node.get(field);
    return n == null || n.isNull() || !n.isValueNode() ? null : n.asText();
  }

  private static String shorten(HttpEntity e) {
    try {
      return e == null ? "" : shorten(EntityUtils.toString(e, StandardCharsets.UTF_8));
    } catch (Exception ex) {
      return "";
    }
  }

  private static String shorten(String body) {
    if (body == null) return "";
    body = body.strip();
    return body.length() > 500 ? body.substring(0, 500) + "…" : body;
  }
}
