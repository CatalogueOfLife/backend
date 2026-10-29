package life.catalogue.resources.dataset;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetInfoCache;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/dataset/{key}/import")
@SuppressWarnings("static-method")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DatasetImportResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetImportResource.class);
  private final DatasetImportDao diDao;

  public DatasetImportResource(DatasetImportDao diDao) {
    this.diDao = diDao;
  }


  /**
   * The import attempts of a dataset, newest first. Defaults to the latest attempt only, so limit and offset
   * are plain parameters rather than a {@link Page} bean param, which defaults to a limit of 10.
   * See https://github.com/CatalogueOfLife/backend/issues/1647
   */
  @GET
  public ResultPage<DatasetImport> getImports(@PathParam("key") int key,
                                              @Valid @BeanParam JobSearchRequest req,
                                              @QueryParam("offset") @DefaultValue("0") int offset,
                                              @QueryParam("limit") @DefaultValue("1") int limit) {
    var page = new Page(offset, limit);
    // a release? use mother project in that case
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(key);
    if (info.origin.isRelease()) {
      return releasePage(diDao.getReleaseAttempt(key), page);

    } else {
      req.setDatasetKey(key);
      return diDao.list(req, page);
    }
  }

  /**
   * A release has a single attempt, the project's one it was built from, so only the first page holds it.
   * Returning it for every offset kept a client paging until a short page from ever stopping.
   */
  @VisibleForTesting
  static ResultPage<DatasetImport> releasePage(@Nullable DatasetImport attempt, Page page) {
    // the release points at a project attempt whose metrics row may be gone - List.of would NPE
    if (attempt == null) {
      return new ResultPage<>(page, 0, List.of());
    }
    var result = page.getOffset() == 0 && page.getLimit() > 0 ? List.of(attempt) : List.<DatasetImport>of();
    return new ResultPage<>(page, 1, result);
  }

  @GET
  @Path("{attempt}")
  public DatasetImport getImportAttempt(@PathParam("key") int key,
                                        @PathParam("attempt") int attempt) {
    return diDao.getAttempt(key, attempt);
  }
  
  @GET
  @Path("{attempt}/tree")
  @Produces({MediaType.TEXT_PLAIN})
  public Stream<String> getImportAttemptTree(@PathParam("key") int key,
                                     @PathParam("attempt") int attempt) throws IOException {
    return diDao.getFileMetricsDao().getTree(key, attempt);
  }
  
  @GET
  @Path("{attempt}/names")
  @Produces({MediaType.TEXT_PLAIN})
  public Stream<String> getImportAttemptNames(@PathParam("key") int key,
                                              @PathParam("attempt") int attempt) {
    return diDao.getFileMetricsDao().getNames(key, attempt);
  }
}
