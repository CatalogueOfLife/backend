package life.catalogue.resources;

import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.dao.DuplicateDao;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.VaryAccept;

import java.util.stream.Stream;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public int count(@BeanParam @Valid DuplicateDao.DuplicateRequest req) {
    return dao.count(req);
  }

  @GET
  @VaryAccept
  public ResultPage<Duplicate> find(@Valid @BeanParam DuplicateDao.DuplicateRequest req, @Valid @BeanParam Page page) {
    return dao.page(req, page);
  }

  @GET
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> download(@BeanParam @Valid DuplicateDao.DuplicateRequest req) {
    return dao.stream(req);
  }
  
}
