package life.catalogue.resources;

import life.catalogue.db.tree.BaseDiffService;
import life.catalogue.db.tree.NamesDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.Reader;

@SuppressWarnings("static-method")
@Produces(MediaType.APPLICATION_JSON)
public class AbstractDiffResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractDiffResource.class);
  private final BaseDiffService diff;

  public AbstractDiffResource(BaseDiffService diff) {
    this.diff = diff;
  }

  @GET
  @Path("tree")
  @Produces(MediaType.TEXT_PLAIN)
  public Reader diffTree(@PathParam("key") int key,
                         @QueryParam("attempts") String attempts) throws IOException {
    return diff.treeDiff(key, attempts);
  }

  @GET
  @Path("names")
  public Reader diffNames(@PathParam("key") int key,
                             @QueryParam("attempts") String attempts) throws IOException {
    return diff.namesDiff(key, attempts);
  }

  @GET
  @Path("ids")
  public NamesDiff diffNameIds(@PathParam("key") int key,
                               @QueryParam("attempts") String attempts) throws IOException {
    return diff.nameIdsDiff(key, attempts);
  }

}
