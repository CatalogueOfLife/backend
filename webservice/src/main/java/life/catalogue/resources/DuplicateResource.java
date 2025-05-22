package life.catalogue.resources;

import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.DuplicateDao;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/dataset/{key}/duplicate")
@Produces(MediaType.APPLICATION_JSON)
public class DuplicateResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateResource.class);
  private final DuplicateDao dao;

  public DuplicateResource(DuplicateDao dao) {
    this.dao = dao;
  }

  @GET
  @Path("/count")
  public int count(@BeanParam @Valid DuplicateDao.DuplicateRequest req,
                   @Context ContainerRequestContext ctx) {
    setCacheable(ctx, req);
    return dao.count(req);
  }

  @GET
  @VaryAccept
  public ResultPage<Duplicate> find(@Valid @BeanParam DuplicateDao.DuplicateRequest req,
                                    @Valid @BeanParam Page page,
                                    @Context ContainerRequestContext ctx) {
    setCacheable(ctx, req);
    return dao.page(req, page);
  }

  @GET
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> download(@BeanParam @Valid DuplicateDao.DuplicateRequest req,
                                   @Context ContainerRequestContext ctx) {
    setCacheable(ctx, req);
    return dao.stream(req);
  }

  /**
   * By default the CacheControlResponseFilter will make all call to release and external datasets cacheable.
   * Only project resources are not cached.
   * This can be overriden by setting the CacheControlResponseFilter.DONT_CACHE_PROPERTY property to any non null value.
   * @param ctx
   * @param req
   */
  private static void setCacheable(ContainerRequestContext ctx, DuplicateDao.DuplicateRequest req){
    if(req.withDecision != null || req.projectKey != null) {
      ResourceUtils.dontCache(ctx);
    }
  }
}
