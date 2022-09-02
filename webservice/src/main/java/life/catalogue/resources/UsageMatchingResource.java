package life.catalogue.resources;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.assembly.UsageMatch;
import life.catalogue.assembly.UsageMatcherGlobal;

import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import java.util.List;

@Path("/dataset/{key}/matching")
@SuppressWarnings("static-method")
public class UsageMatchingResource {

  private final UsageMatcherGlobal matcher;

  public UsageMatchingResource(UsageMatcherGlobal matcher) {
    this.matcher = matcher;
  }

  @GET
  public UsageMatch projectSourceMetrics(@PathParam("key") int datasetKey,
                                         @QueryParam("name") String name,
                                         @QueryParam("authorship") String authorship,
                                         @QueryParam("code") NomCode code,
                                         @QueryParam("rank") Rank rank
  ) throws InterruptedException {
    //TODO: get from query params - provider
    Classification classification = new Classification();

    Name n = NameMatchingResource.name(name, authorship, rank, code);
    Taxon t = new Taxon(n);
    return matcher.match(datasetKey, t, List.of());
  }
}
