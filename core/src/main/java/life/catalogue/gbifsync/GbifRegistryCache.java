package life.catalogue.gbifsync;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Publisher;
import life.catalogue.api.vocab.area.Country;
import life.catalogue.config.GbifConfig;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Shared, long lived cache of GBIF registry organisations and installations.
 * A single instance is reused across all GBIF sync runs (dataset and publisher syncs) so the same
 * publisher/host organisations are not re-fetched from the slow registry on every run. The raw
 * organisation is cached once and used both as a publisher {@link Agent} (for datasets) and as a
 * {@link Publisher} entity (for the publisher sync), so each organisation is fetched at most once
 * per TTL window regardless of which sync needs it. Entries expire after a configurable number of
 * hours so organisation metadata still refreshes over time.
 */
public class GbifRegistryCache {
  private static final Logger LOG = LoggerFactory.getLogger(GbifRegistryCache.class);
  private static final long DEFAULT_TTL_HOURS = 12;
  private static final int MAX_SIZE = 10_000;

  private final WebTarget organization;
  private final WebTarget installation;
  // raw organisation as returned by GBIF - the single source for both publisher Agents and Publisher entities
  private final LoadingCache<UUID, DatasetPager.GAgent> orgCache;
  // installation key -> host Agent (derived from the installation's organisation)
  private final LoadingCache<UUID, Agent> hostCache;

  public GbifRegistryCache(Client client, GbifConfig cfg) {
    this.organization = client.target(UriBuilder.fromUri(cfg.api).path("/organization/"));
    this.installation = client.target(UriBuilder.fromUri(cfg.api).path("/installation/"));
    long ttl = cfg.registryCacheHours > 0 ? cfg.registryCacheHours : DEFAULT_TTL_HOURS;
    this.orgCache = Caffeine.newBuilder()
      .maximumSize(MAX_SIZE)
      .expireAfterWrite(ttl, TimeUnit.HOURS)
      .build(this::loadOrg);
    this.hostCache = Caffeine.newBuilder()
      .maximumSize(MAX_SIZE)
      .expireAfterWrite(ttl, TimeUnit.HOURS)
      .build(this::loadHost);
    LOG.info("Created GBIF registry cache with {}h TTL", ttl);
  }

  /** @return the publisher organisation as an Agent, or null if the key is null or has no usable name */
  public @Nullable Agent publisher(@Nullable UUID orgKey) {
    if (orgKey == null) return null;
    DatasetPager.GAgent g = orgCache.get(orgKey);
    return g == null ? null : g.toAgent();
  }

  /** @return the publisher organisation as a Publisher entity, or null if the key is null */
  public @Nullable Publisher publisherEntity(@Nullable UUID orgKey) {
    if (orgKey == null) return null;
    DatasetPager.GAgent g = orgCache.get(orgKey);
    return g == null ? null : toPublisher(orgKey, g);
  }

  /** @return the host organisation behind an installation as an Agent, or null */
  public @Nullable Agent host(@Nullable UUID installationKey) {
    if (installationKey == null) return null;
    return hostCache.get(installationKey);
  }

  private DatasetPager.GAgent loadOrg(UUID key) {
    WebTarget t = organization.path(key.toString());
    LOG.debug("Retrieve organization {}", t.getUri());
    return t.request()
            .accept(MediaType.APPLICATION_JSON_TYPE)
            .get(DatasetPager.GAgent.class);
  }

  private Agent loadHost(UUID installationKey) {
    WebTarget t = installation.path(installationKey.toString());
    LOG.debug("Retrieve installation {}", t.getUri());
    DatasetPager.GInstallation ins = t.request()
                                      .accept(MediaType.APPLICATION_JSON_TYPE)
                                      .get(DatasetPager.GInstallation.class);
    if (ins != null && ins.organizationKey != null) {
      Agent host = publisher(ins.organizationKey);
      if (host != null) {
        host.setNote("Host");
      }
      return host;
    }
    return null;
  }

  private static Publisher toPublisher(UUID key, DatasetPager.GAgent g) {
    Publisher p = new Publisher();
    p.setKey(key);
    p.setTitle(g.title);
    p.setDescription(g.description);
    p.setHomepage(StringUtils.trimToNull(g.firstHomepage()));
    p.setCity(g.city);
    p.setProvince(g.province);
    p.setCountry(country(g.country));
    p.setLatitude(parseDouble(g.latitude));
    p.setLongitude(parseDouble(g.longitude));
    return p;
  }

  private static String country(String iso) {
    if (StringUtils.isBlank(iso)) return null;
    return Country.fromIsoCode(iso).map(Country::getName).orElse(iso);
  }

  private static Double parseDouble(String s) {
    if (StringUtils.isBlank(s)) return null;
    try {
      return Double.valueOf(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
