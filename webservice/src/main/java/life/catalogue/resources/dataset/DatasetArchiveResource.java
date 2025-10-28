package life.catalogue.resources.dataset;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dw.jersey.FileStreamingOutput;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.resources.ResourceUtils;

import java.io.File;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/dataset/{key}/archive")
// there are many unofficial mime types around for zip
@Produces({
  MediaType.APPLICATION_OCTET_STREAM,
  MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
})
public class DatasetArchiveResource {
  private final WsServerConfig cfg;

  public DatasetArchiveResource(WsServerConfig cfg) {
    this.cfg = cfg;
  }

  @GET
  public Response archive(@PathParam("key") int key, @QueryParam("attempt") Integer attempt) {
    File source;
    if (attempt == null) {
      source = cfg.normalizer.lastestArchiveSymlink(key);
    } else {
      source = cfg.normalizer.archive(key, attempt);
    }
    if (!source.exists()) {
      String msg = attempt == null ? "Archive for dataset " + key + " not found" : "Archive for import attempt " + attempt + " of dataset " + key + " not found";
      throw new NotFoundException(key, msg);
    }

    StreamingOutput stream = new FileStreamingOutput(source);
    return Response.ok(stream)
      .type(MoreMediaTypes.APP_ZIP)
      .header(MoreHttpHeaders.CONTENT_DISPOSITION, ResourceUtils.fileAttachment("dataset-" + key + ".zip"))
      .build();
  }

}
