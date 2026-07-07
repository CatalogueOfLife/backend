package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.vocab.Datasets;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.config.MatchingConfig;
import life.catalogue.es.suggest.NameUsageSuggestionService;
import life.catalogue.matching.UsageMatcherFactory;

import java.net.URI;

import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Default OpenRefine reconciliation endpoint pointing at the COL backbone, i.e. the latest public
 * extended release of the COL project ({@link Datasets#COL}). The concrete release key is resolved
 * per request so the bare {@code /reconcile} URL always tracks the current XR, while its manifest
 * keeps advertising the stable {@code /reconcile} sub-service URLs.
 */
@Path("/reconcile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DefaultReconciliationResource extends AbstractReconciliationResource {
  private final LatestDatasetKeyCache cache;

  public DefaultReconciliationResource(MatchingConfig cfg, NameUsageSuggestionService suggestService, SqlSessionFactory factory,
                                       UsageMatcherFactory matcherFactory, LatestDatasetKeyCache cache, URI apiURI, URI clbURI) {
    super(cfg, suggestService, factory, matcherFactory, apiURI, clbURI);
    this.cache = cache;
  }

  @Override
  protected int resolveDatasetKey(int pathKey) {
    Integer key = cache.getLatestRelease(Datasets.COL, true);
    if (key == null) {
      throw new NotFoundException("No COL extended release available to reconcile against");
    }
    return key;
  }

  @Override
  protected String reconcileBaseUrl(int datasetKey) {
    return apiBase() + "/reconcile";
  }
}
