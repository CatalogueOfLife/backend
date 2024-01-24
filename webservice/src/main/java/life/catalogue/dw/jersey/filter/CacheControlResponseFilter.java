package life.catalogue.dw.jersey.filter;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Filter that updates Cache-Control http headers to allow caching of responses belonging to static released datasets.
 * The following headers are added or replaced if they existed:
 */
public class CacheControlResponseFilter implements ContainerResponseFilter {
  private static final long AGE1 = TimeUnit.HOURS.toSeconds(1);
  private static final long AGE24 = TimeUnit.HOURS.toSeconds(24);
  private static final Pattern STATIC_PATH  = Pattern.compile("^(vocab|openapi|version)");
  private static final Set<String> METHODS  = Set.of(HttpMethod.GET, HttpMethod.HEAD);

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp) throws IOException {
    if (req.getMethod() != null && METHODS.contains(req.getMethod())) {
      if (STATIC_PATH.matcher(req.getUriInfo().getPath()).find()) {
        allowCaching(resp, AGE1);
        return;
      }
      Integer datasetKey = FilterUtils.datasetKeyOrNull(req.getUriInfo());
      if (datasetKey != null) {
        var info = DatasetInfoCache.CACHE.info(datasetKey, true);
        if (info.origin.isRelease()) {
          // its a release, we can cache it for longer!
          allowCaching(resp, AGE24);
          return;
        } else if (info.origin == DatasetOrigin.EXTERNAL) {
          // thats also rather static information, but allow it to change more often
          allowCaching(resp, AGE1);
          return;
        }
      }
    }
    preventCaching(resp);
  }

  private void allowCaching(ContainerResponseContext resp, long ageInSeconds){
    resp.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "public, max-age=" + ageInSeconds + ", s-maxage=" + ageInSeconds);
  }

  private void preventCaching(ContainerResponseContext resp){
    resp.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
  }

}
