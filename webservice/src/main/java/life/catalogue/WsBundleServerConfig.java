package life.catalogue;

import life.catalogue.config.MailConfig;
import life.catalogue.dw.auth.map.MapAuthenticationFactory;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.validation.constraints.Min;

/**
 * Configuration of the {@link WsBundleServer}, the single release "CLB in a box" bundle.
 *
 * It is a regular {@link WsServerConfig} so the entire shared read only stack - jersey bundles, DAOs and
 * {@link WsROServer#registerReadOnlyResources} - can be reused unchanged. All this class adds is the one
 * release key the bundle serves plus defaults for everything a bundle does not have: no GBIF registry to
 * authenticate against, no mail server, and the in-container paths of the shipped data volume.
 *
 * Unlike the other apps a bundle REQUIRES elasticsearch, see {@link WsBundleServer#esRequired()}.
 */
public class WsBundleServerConfig extends WsServerConfig {

  /**
   * The single release dataset this bundle serves. Requests without a dataset key are rewritten to it,
   * see {@link life.catalogue.dw.jersey.filter.SingleDatasetRewriteFilter}.
   */
  @Min(1)
  public int releaseKey;

  /**
   * If true the release is indexed into elasticsearch on startup when the index holds no documents for it.
   * Set to false when the bundle ships a prewarmed elastic volume.
   */
  public boolean indexOnStart = true;

  public WsBundleServerConfig() {
    // a bundle has no GBIF registry to talk to. An empty user map authenticates nobody, which is all a
    // read only bundle needs - add users to the yaml if you want to log in.
    auth = new MapAuthenticationFactory();

    // no mail server (host stays null), but the @NotNull fields still have to validate
    mail = new MailConfig();
    mail.from = "noreply@localhost";
    mail.fromName = "ChecklistBank Bundle";
    mail.replyTo = "noreply@localhost";
    mail.mailinglist = "noreply@localhost";

    // NOTE: es is deliberately left null. Elastic is mandatory for a bundle, and a config that forgets it
    // should fail with that message rather than with a DNS error against some default host.

    // WsServerConfig ships a short demo key that the JWT library rejects outright (< 256 bits), and a
    // bundle has no sessions worth surviving a restart - so generate a fresh secure one per start.
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    jwtKey = Base64.getEncoder().encodeToString(secret);

    // no DataCite account - a bundle never registers a DOI, the fields are only @NotNull
    doi.username = "none";
    doi.password = "none";

    // the shipped data volume
    img.repo = Path.of("/data/img");
    img.archive = Path.of("/data/img/archive");
    img.apiUrl = URI.create("http://localhost:8080");
    matching.storageDir = new File("/data/matcher");
    matching.uploadDir = new File("/tmp/col/upload");
    // A bundle ships a prebuilt matcher store and must never discard it. With the default threshold the
    // startup reconcile treats a dataset below it as "small", decides it should not have a persistent
    // matcher and deletes the shipped store - which for a small dataset breaks the next start entirely.
    matching.pgMatcherThreshold = 0;
    namesIndex.file = new File("/data/nidx");
    namesIndex.verification = false;
    metricsRepo = new File("/data/metrics");

    // everything a bundle serves is local
    clbURI = URI.create("http://localhost:8080");
    apiURI = URI.create("http://localhost:8080");
    job.downloadURI = URI.create("http://localhost:8080/job/");
  }
}
