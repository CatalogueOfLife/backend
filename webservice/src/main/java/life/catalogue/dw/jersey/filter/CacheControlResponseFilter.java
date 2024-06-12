package life.catalogue.dw.jersey.filter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;

/**
 * Filter that updates Cache-Control http headers to allow caching of responses belonging to static released datasets.
 * Requests to external datasets are also cached for a shorter period.
 *
 * Any resource can override this default behavior and declare a request to be uncacheable by setting the "dont-cache"
 * request context property to any non null value.
 */
public class CacheControlResponseFilter implements ContainerResponseFilter {
  public static final String DONT_CACHE = "dont-cache";
  private static final long AGE_EXTERNAL = TimeUnit.HOURS.toSeconds(1);
  private static final long AGE_RELEASE = TimeUnit.DAYS.toSeconds(7);
  private static final Pattern STATIC_PATH  = Pattern.compile("^(vocab|openapi|version)");
  private static final Set<String> METHODS  = Set.of(HttpMethod.GET, HttpMethod.HEAD);

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp) throws IOException {
    // we allow resources to turn off caching for certain requests by using the dont cache property
    if (req.getMethod() != null && METHODS.contains(req.getMethod()) && req.getProperty(CacheControlResponseFilter.DONT_CACHE) == null) {
      if (STATIC_PATH.matcher(req.getUriInfo().getPath()).find()) {
        allowCaching(resp, AGE_EXTERNAL, true);
        return;
      }
      Integer datasetKey = FilterUtils.datasetKeyOrNull(req.getUriInfo());
      if (datasetKey != null) {
        try {
          var info = DatasetInfoCache.CACHE.info(datasetKey, true);
          if (info.origin.isRelease()) {
            // its a (x)release, we can cache it for longer!
            allowCaching(resp, AGE_RELEASE, true);
            return;
          } else if (info.origin == DatasetOrigin.EXTERNAL) {
            // thats also rather static information, but allow it to change more often
            allowCaching(resp, AGE_EXTERNAL, false);
            return;
          }
        } catch (NotFoundException e) {
          // fall through to prevent caching - e.g. happens when we process an already mapped NotFoundException
        }
      }
    }
    preventCaching(resp);
  }

  private void allowCaching(ContainerResponseContext resp, long ageInSeconds, boolean allowBrowserCache){
    resp.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "public, max-age=" + ageInSeconds + ", s-maxage=" + ageInSeconds);
    if (!allowBrowserCache) {
      resp.getHeaders().putSingle("x-remove-cache-control", "true");
    }
  }

  private void preventCaching(ContainerResponseContext resp){
    resp.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
  }

}
