package life.catalogue.resources;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
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
public abstract class AbstractDiffResource<K> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractDiffResource.class);
  private final BaseDiffService<K> diff;

  public AbstractDiffResource(BaseDiffService<K> diff) {
    this.diff = diff;
  }

  abstract K keyFromPath(DSID<Integer> dsid);

  @GET
  @Path("tree")
  @Produces(MediaType.TEXT_PLAIN)
  public Reader diffTree(@BeanParam DSIDValue<Integer> key,
                         @QueryParam("attempts") String attempts) throws IOException {
    return diff.treeDiff(keyFromPath(key), attempts);
  }

  @GET
  @Path("names")
  @Produces(MediaType.TEXT_PLAIN)
  public Reader diffNames(@BeanParam DSIDValue<Integer> key,
                          @QueryParam("attempts") String attempts) throws IOException {
    return diff.namesDiff(keyFromPath(key), attempts);
  }

  @GET
  @Path("ids")
  public NamesDiff diffNameIds(@BeanParam DSIDValue<Integer> key,
                               @QueryParam("attempts") String attempts) throws IOException {
    return diff.nameIdsDiff(keyFromPath(key), attempts);
  }

}
