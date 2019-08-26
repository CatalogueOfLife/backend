package org.col.resources;

import java.util.Optional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.col.matching.NameIndex;
import org.col.api.model.IssueContainer;
import org.col.api.model.Name;
import org.col.api.model.NameAccordingTo;
import org.col.api.model.NameMatch;
import org.col.parser.NameParser;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/name/matching")
@Produces(MediaType.APPLICATION_JSON)
public class MatchingResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(MatchingResource.class);
  private final NameIndex ni;
  
  public MatchingResource(NameIndex ni) {
    this.ni = ni;
  }
  
  /**
   * Parsing citations as GET query parameters.
   */
  @GET
  public NameMatch match(@QueryParam("q") String q,
                         @QueryParam("rank") Rank rank,
                         @QueryParam("code") NomCode code,
                         @QueryParam("trusted") boolean trusted,
                         @QueryParam("verbose") boolean verbose) {
    // TODO: remove trusted param - this is for tests only at this stage
    // trusted inserts should only happen during imports, not exposing public webservices.
    Name n = name(q, rank, code);
    NameMatch m = ni.match(n, trusted, verbose);
    LOG.debug("Matching {} to {}", n.canonicalNameComplete(), m);
    return m;
  }
  
  static Name name(String name, Rank rank, NomCode code) {
    Optional<NameAccordingTo> opt = NameParser.PARSER.parse(name, rank, code, IssueContainer.VOID);
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
