package life.catalogue.resources.dataset;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.jersey.FileStreamingOutput;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import life.catalogue.resources.ResourceUtils;

import java.io.File;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.annotations.VisibleForTesting;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

/**
 * Serves the original source archive of a dataset as it was uploaded or downloaded for an import.
 * Without an explicit attempt it is the archive of the last successful import, the one the dataset's data comes from.
 * Datasets ChecklistBank registered in GBIF use this as their GBIF data endpoint, so a broken upload never reaches GBIF.
 */
@Path("/dataset/{key}/archive")
// there are many unofficial mime types around for zip
@Produces({
  MediaType.APPLICATION_OCTET_STREAM,
  MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
})
public class DatasetArchiveResource {
  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;

  public DatasetArchiveResource(WsServerConfig cfg, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
  }

  @GET
  public Response archive(@PathParam("key") int key, @QueryParam("attempt") Integer attempt) {
    File source;
    if (attempt == null) {
      Integer current;
      try (SqlSession session = factory.openSession()) {
        current = session.getMapper(DatasetMapper.class).lastImportAttempt(key);
      }
      source = currentArchive(cfg.normalizer, key, current);
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

  /**
   * @param currentAttempt the attempt of the last successful import, i.e. dataset.attempt
   * @return the archive of the last successful import, or the latest stored archive if there is none,
   *   e.g. for a dataset whose imports all failed or whose archive is gone
   */
  @VisibleForTesting
  static File currentArchive(NormalizerConfig cfg, int key, @Nullable Integer currentAttempt) {
    if (currentAttempt != null) {
      File f = cfg.archive(key, currentAttempt);
      if (f.exists()) {
        return f;
      }
    }
    return cfg.lastestArchiveSymlink(key);
  }
}
