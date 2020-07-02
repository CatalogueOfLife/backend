package life.catalogue.dw.jersey.filter;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter that updates Cache-Control http headers to allow caching of responses belonging to static released datasets.
 * The following headers are added or replaced if they existed:
 */
public class CacheControlResponseFilter implements ContainerResponseFilter {
  private static final Logger LOG = LoggerFactory.getLogger(CacheControlResponseFilter.class);
  private static final long MAX_AGE = TimeUnit.HOURS.toSeconds(24);
  // age in seconds
  private static final String CACHE24 = "public, max-age=" + MAX_AGE + ", s-maxage=" + MAX_AGE;
  private static final Pattern DATASET_PATH  = Pattern.compile("dataset/(\\d+)");
  private static final Set<String> METHODS  = Set.of(HttpMethod.GET, HttpMethod.OPTIONS, HttpMethod.HEAD);
  private final IntSet releases = new IntOpenHashSet();

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp) throws IOException {
    if (req.getMethod() != null && METHODS.contains(req.getMethod())) {
      Matcher m = DATASET_PATH.matcher(req.getUriInfo().getPath());
      if (m.find()) {
        // parsing cannot fail, we have a pattern
        int datasetKey = Integer.parseInt(m.group(1));
        if (releases.contains(datasetKey)) {
          // its a release, we can cache it!
          allowCaching(resp);
          return;
        }
      }
    }
    preventCaching(resp);
  }

  public boolean add(int key) {
    LOG.info("Added release {}", key);
    return releases.add(key);
  }

  public boolean addAll(@NotNull Collection<? extends Integer> keys) {
    LOG.info("Added {} release keys", keys.size());
    return releases.addAll(keys);
  }

  private void allowCaching(ContainerResponseContext resp){
    resp.getHeaders().putSingle("Cache-Control", CACHE24);
  }

  private void preventCaching(ContainerResponseContext resp){
    resp.getHeaders().putSingle("Cache-Control", "no-store");
  }

  public void addRelease(int datasetKey){
    add(datasetKey);
  }
}
