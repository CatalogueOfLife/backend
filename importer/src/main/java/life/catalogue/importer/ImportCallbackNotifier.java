package life.catalogue.importer;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.config.ImporterConfig;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires a single, asynchronous HTTP POST carrying the final DatasetImport JSON to an optional,
 * client-supplied callback URL once an import or validation reaches a terminal state (success or failure).
 * <p>
 * The payload is the exact same JSON as returned by {@code GET /dataset/{key}/import}, so clients that
 * submitted an archive and provided a callback need no follow-up fetch.
 * <p>
 * Only a single attempt is made and any failure (serialization, connection, timeout, non-2xx response)
 * is logged and swallowed so a dead or slow receiver can never block or fail an importer thread.
 * See <a href="https://github.com/CatalogueOfLife/backend/issues/1552">issue #1552</a>.
 */
public class ImportCallbackNotifier implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(ImportCallbackNotifier.class);
  static final String THREAD_NAME = "import-callback";

  private final CloseableHttpClient client;
  private final RequestConfig requestConfig;
  private final ExecutorService exec;

  public ImportCallbackNotifier(CloseableHttpClient client, ImporterConfig cfg) {
    this.client = client;
    Timeout timeout = Timeout.ofSeconds(cfg.callbackTimeout);
    this.requestConfig = RequestConfig.custom()
      .setConnectTimeout(timeout)
      .setResponseTimeout(timeout)
      .build();
    this.exec = Executors.newFixedThreadPool(2, new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true));
  }

  /**
   * Asynchronously serializes and POSTs the given DatasetImport as JSON to the callback URL.
   * A null callback is a no-op. This method never throws - failures are logged only.
   */
  public void notifyCallback(URI callback, DatasetImport di) {
    if (callback == null) {
      return;
    }
    final Integer datasetKey = di == null ? null : di.getDatasetKey();
    final String json;
    try {
      json = ApiModule.MAPPER.writeValueAsString(di);
    } catch (Exception e) {
      LOG.warn("Failed to serialize DatasetImport for callback {} of dataset {}", callback, datasetKey, e);
      return;
    }
    exec.submit(() -> post(callback, json, datasetKey));
  }

  private void post(URI callback, String json, Integer datasetKey) {
    HttpPost post = new HttpPost(callback);
    post.setConfig(requestConfig);
    post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    try (CloseableHttpResponse resp = client.execute(post)) {
      int code = resp.getCode();
      if (code >= 200 && code < 300) {
        LOG.info("Notified callback {} of import completion for dataset {}, status {}", callback, datasetKey, code);
      } else {
        LOG.warn("Callback {} for dataset {} import completion returned status {}", callback, datasetKey, code);
      }
    } catch (Exception e) {
      LOG.warn("Failed to notify callback {} of import completion for dataset {}: {}", callback, datasetKey, e.getMessage());
    }
  }

  @Override
  public void close() {
    exec.shutdown();
    try {
      if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
        exec.shutdownNow();
      }
    } catch (InterruptedException e) {
      exec.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
