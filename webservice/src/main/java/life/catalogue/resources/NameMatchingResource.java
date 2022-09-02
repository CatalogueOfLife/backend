package life.catalogue.resources;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.matching.NameIndex;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/name/matching")
@Produces(MediaType.APPLICATION_JSON)
public class NameMatchingResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameMatchingResource.class);
  private final NameIndex ni;
  
  public NameMatchingResource(NameIndex ni) {
    this.ni = ni;
  }
  
  /**
   * Parsing citations as GET query parameters.
   */
  @GET
  public NameMatch match(@QueryParam("q") String q,
                         @QueryParam("authorship") String authorship,
                         @QueryParam("rank") Rank rank,
                         @QueryParam("code") NomCode code,
                         @QueryParam("verbose") boolean verbose) throws InterruptedException {
    Name n = name(q, authorship, rank, code);
    NameMatch m = ni.match(n, false, verbose);
    LOG.debug("Matching {} to {}", n.getLabel(), m);
    return m;
  }
  
  static Name name(String name, String authorship, Rank rank, NomCode code) throws InterruptedException {
    Optional<ParsedNameUsage> opt = NameParser.PARSER.parse(name, authorship, rank, code, IssueContainer.VOID);
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      // use parser determined code and rank in case nothing was given explicitly
      if (rank != null) {
        n.setRank(rank);
      }
      if (code != null) {
        n.setCode(code);
      }
      return n;
      
    } else {
      throw new IllegalArgumentException("Unable to parse name: " + name);
    }
  }
  
}
