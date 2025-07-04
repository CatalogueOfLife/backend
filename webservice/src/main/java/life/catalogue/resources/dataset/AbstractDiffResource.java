package life.catalogue.resources.dataset;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.printer.BaseDiffService;

import java.io.IOException;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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
  @Produces(MediaType.TEXT_PLAIN)
  public Reader diffNames(@BeanParam DSIDValue<Integer> key,
                          @QueryParam("attempts") String attempts) throws IOException {
    return diff.diff(keyFromPath(key), attempts);
  }

}
