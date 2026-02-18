package life.catalogue.importer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.TempFile;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.nginx.NginxContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Spins up a NGINX test container
 * serving a ColDP and DwC archive.
 * To be used as a ClassRule.
 */
public class NginxRule extends ExternalResource {

  public static String IMAGE = "nginx:1.29.5-alpine";

  private static final Logger LOG = LoggerFactory.getLogger(NginxRule.class);

  private static NginxContainer CONTAINER;
  private TempFile contentFolder;

  @Override
  protected void before() throws Throwable {
    super.before();
    contentFolder = TempFile.directory();
    CONTAINER = setupNginx();
    CONTAINER.start();
    LOG.info("Started embedded Nginx");
  }

  private NginxContainer setupNginx() throws IOException {
    // Copy archives to html docs folder
    Path dir = contentFolder.file.toPath();
    Resources.copy("coldp/test.zip", new File(contentFolder.file, "coldp.zip"));
    Resources.copy("dwca/plazi-dwca.zip", new File(contentFolder.file, "dwca.zip"));
    return new NginxContainer(IMAGE)
      .withCopyFileToContainer(MountableFile.forHostPath(dir), "/usr/share/nginx/html")
      .waitingFor(new HttpWaitStrategy());
  }

  public URL getBaseUrl() {
    try {
      return CONTAINER.getBaseUrl("http", 80);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public URI getArchive(DataFormat format) {
    try {
      URI uri = getBaseUrl().toURI();
      return uri.resolve(format.getName().toLowerCase()+".zip");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void after() {
    super.after();
    if (contentFolder != null) {
      contentFolder.close();
    }
    CONTAINER.stop();
  }
}
