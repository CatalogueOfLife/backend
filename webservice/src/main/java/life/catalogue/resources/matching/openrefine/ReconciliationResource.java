package life.catalogue.resources.matching.openrefine;

import life.catalogue.config.MatchingConfig;
import life.catalogue.es.suggest.NameUsageSuggestionService;
import life.catalogue.matching.UsageMatcherFactory;

import java.net.URI;

import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Dataset-scoped OpenRefine reconciliation endpoint, reconciling against any dataset or release.
 * All behaviour is inherited; this class only binds the dataset-scoped {@code @Path}.
 */
@Path("/dataset/{key}/reconcile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReconciliationResource extends AbstractReconciliationResource {

  public ReconciliationResource(MatchingConfig cfg, NameUsageSuggestionService suggestService, SqlSessionFactory factory,
                                UsageMatcherFactory matcherFactory, URI apiURI, URI clbURI) {
    super(cfg, suggestService, factory, matcherFactory, apiURI, clbURI);
  }
}
