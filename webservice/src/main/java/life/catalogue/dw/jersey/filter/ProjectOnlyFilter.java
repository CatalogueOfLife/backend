package life.catalogue.dw.jersey.filter;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.jersey.exception.JsonExceptionMapperBase;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter to reject requests against any datasets which are not projects.
 * Primarily used to reject calls that would modify the data of releases or external datasets.
 * Looks at method annotations @ProjectOnly to determine if the request is potentially changing data
 * and returns a 405 response if the request is aborted.
 */
@Provider
@ProjectOnly
public class ProjectOnlyFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    // extract dataset key - must exist because we only annotated methods that are on the dataset path
    int datasetKey = FilterUtils.datasetKey(req.getUriInfo());
    var origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (origin != DatasetOrigin.PROJECT) {
      req.abortWith(JsonExceptionMapperBase.jsonErrorResponse(Response.Status.METHOD_NOT_ALLOWED,
        "Dataset " + datasetKey + " is not a project")
      );
    }
  }
}
