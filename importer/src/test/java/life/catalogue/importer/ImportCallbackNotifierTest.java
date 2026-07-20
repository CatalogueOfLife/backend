package life.catalogue.importer;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.config.ImporterConfig;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImportCallbackNotifierTest {

  private static DatasetImport datasetImport() {
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(1000);
    di.setAttempt(3);
    di.setState(ImportState.FINISHED);
    return di;
  }

  @Test
  public void postsDatasetImportJson() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> received = new AtomicReference<>();
    AtomicReference<String> contentType = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    server.createContext("/hook", ex -> {
      try (InputStream in = ex.getRequestBody()) {
        received.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
      contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
      ex.sendResponseHeaders(200, -1);
      ex.close();
      latch.countDown();
    });
    server.start();
    int port = server.getAddress().getPort();

    DatasetImport di = datasetImport();
    try (CloseableHttpClient client = HttpClients.createDefault();
         ImportCallbackNotifier notifier = new ImportCallbackNotifier(client, new ImporterConfig())) {
      notifier.notifyCallback(URI.create("http://127.0.0.1:" + port + "/hook"), di);
      assertTrue("callback should have been received", latch.await(10, TimeUnit.SECONDS));
    } finally {
      server.stop(0);
    }

    // the posted body must be exactly the DatasetImport JSON a client would otherwise poll for
    assertEquals(ApiModule.MAPPER.writeValueAsString(di), received.get());
    assertTrue("json content type expected but was " + contentType.get(),
      contentType.get() != null && contentType.get().startsWith("application/json"));
  }

  @Test
  public void nullCallbackIsNoop() throws Exception {
    try (CloseableHttpClient client = HttpClients.createDefault();
         ImportCallbackNotifier notifier = new ImportCallbackNotifier(client, new ImporterConfig())) {
      // must not throw
      notifier.notifyCallback(null, datasetImport());
    }
  }

  @Test
  public void swallowsDeliveryFailures() throws Exception {
    try (CloseableHttpClient client = HttpClients.createDefault();
         ImportCallbackNotifier notifier = new ImportCallbackNotifier(client, new ImporterConfig())) {
      // nothing is listening -> connection refused; the async single attempt must be swallowed, never propagated
      notifier.notifyCallback(URI.create("http://127.0.0.1:1/hook"), datasetImport());
      TimeUnit.MILLISECONDS.sleep(200);
    }
  }
}
