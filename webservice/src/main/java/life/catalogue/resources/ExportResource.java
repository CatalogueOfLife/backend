package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dw.jersey.MoreMediaTypes;

import java.util.UUID;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/export")
@Produces(MediaType.APPLICATION_JSON)
public class ExportResource {
  private final WsServerConfig cfg;
  private final DatasetExportDao dao;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ExportResource.class);

  public ExportResource(DatasetExportDao dao, WsServerConfig cfg) {
    this.cfg = cfg;
    this.dao = dao;
  }

  @GET
  public ResultPage<DatasetExport> list(@BeanParam ExportSearchRequest filter, @Valid @BeanParam Page page) {
    return dao.list(filter, page);
  }

  @GET
  @Path("{id}")
  public DatasetExport get(@PathParam("id") UUID key) {
    return dao.get(key);
  }

  @GET
  @Path("{id}")
  // there are many unofficial mime types around for zip, support them all
  @Produces({
    MediaType.APPLICATION_OCTET_STREAM,
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  public Response redirectToExportFile(@PathParam("id") UUID key) {
    return Response.status(Response.Status.FOUND)
      .location(DatasetExport.downloadURI(key))
      .build();
  }

}
