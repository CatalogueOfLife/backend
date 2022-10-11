package life.catalogue.resources;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.assembly.UsageMatch;
import life.catalogue.assembly.UsageMatcherGlobal;

import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.*;

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
                                         @QueryParam("rank") Rank rank,
                                         @BeanParam Classification classification
  ) throws InterruptedException {
    Name n = NameMatchingResource.name(name, authorship, rank, code);
    Taxon t = new Taxon(n);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    return matcher.match(datasetKey, t, classification);
  }
}
