package life.catalogue.resources;

import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.DuplicateDao;
import life.catalogue.dw.jersey.filter.VaryAccept;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.util.stream.Stream;

@Path("/dataset/{key}/source/{id}/duplicate")
@Produces(MediaType.APPLICATION_JSON)
public class DuplicateSourceResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateSourceResource.class);
  private final DuplicateDao dao;

  public DuplicateSourceResource(DuplicateDao dao) {
    this.dao = dao;
  }

  private static DuplicateDao.DuplicateRequest upd(int projectKey, int sourceKey, DuplicateDao.DuplicateRequest req) {
    req.setProjectKey(projectKey);
    req.setDatasetKey(sourceKey);
    return req;
  }

  @GET
  @Path("/count")
  public int count(@PathParam("key") int projectKey,
                   @PathParam("id") int sourceKey,
                   @BeanParam @Valid DuplicateDao.DuplicateRequest req) {
    return dao.count(upd(projectKey, sourceKey, req));
  }

  @GET
  @VaryAccept
  public ResultPage<Duplicate> find(@PathParam("key") int projectKey,
                                    @PathParam("id") int sourceKey,
                                    @Valid @BeanParam DuplicateDao.DuplicateRequest req, @Valid @BeanParam Page page) {
    return dao.page(upd(projectKey, sourceKey, req), page);
  }

  @GET
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> download(@PathParam("key") int projectKey,
                                   @PathParam("id") int sourceKey,
                                   @BeanParam @Valid DuplicateDao.DuplicateRequest req) {
    return dao.stream(upd(projectKey, sourceKey, req));
  }
  
}
