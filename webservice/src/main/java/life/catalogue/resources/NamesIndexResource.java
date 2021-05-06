package life.catalogue.resources;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.matching.NameIndex;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Collection;
import java.util.Optional;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/nidx")
@Produces(MediaType.APPLICATION_JSON)
public class NamesIndexResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexResource.class);
  private final NameIndex ni;

  public NamesIndexResource(NameIndex ni) {
    this.ni = ni;
  }

  @GET
  @Path("{key}")
  public IndexName get(@PathParam("key") int key) {
    return ni.get(key);
  }

  @GET
  @Path("{key}/group")
  public Collection<IndexName> byCanonical(@PathParam("key") int key) {
    return ni.byCanonical(key);
  }

  @GET
  @Path("/match")
  public NameMatch match(@QueryParam("q") String q,
                         @QueryParam("scientificName") String scientificName,
                         @QueryParam("authorship") String authorship,
                         @QueryParam("rank") Rank rank,
                         @QueryParam("code") NomCode code,
                         @QueryParam("verbose") boolean verbose) {
    Name n = name(ObjectUtils.coalesce(scientificName, q), authorship, rank, code);
    NameMatch m = ni.match(n, false, verbose);
    LOG.debug("Matching {} to {}", n.getLabel(), m);
    return m;
  }
  
  static Name name(String name, String authorship, Rank rank, NomCode code) {
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
