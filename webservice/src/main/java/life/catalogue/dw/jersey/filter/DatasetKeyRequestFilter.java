package life.catalogue.dw.jersey.filter;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter that parses dataset keys given as path or query parameters
 * and rewrites them if needed to support conventions to refer to
 *  - the latest release of a project: {projectKey}LR
 */
@PreMatching
public class DatasetKeyRequestFilter implements ContainerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetKeyRequestFilter.class);

  private static final String LATEST_RELEASE_SUFFIX  = "LR";
  private static final Pattern LR_PATH  = Pattern.compile("dataset/(\\d+)" + LATEST_RELEASE_SUFFIX);
  // all parameters that contain dataset keys and which we check if they need to be rewritten
  private static final Set<String> QUERY_PARAMS  = Set.of("datasetKey", "catalogueKey", "projectKey", "subjectDatasetKey");

  private SqlSessionFactory factory;
  private final LoadingCache<Integer, Integer> latest = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(60, TimeUnit.MINUTES)
    .build(this::lookupLatest);

  // we dont use a limited loading cache here as
  //  - the primitive map saves a lot of memory and we dont really have many datasets so we can keep them all in memory
  //  - a dataset origin is immutable so we dont need to expire
  private final Int2BooleanMap managed = Int2BooleanMaps.synchronize(new Int2BooleanOpenHashMap());

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    UriBuilder builder = req.getUriInfo().getRequestUriBuilder();
    final URI original = builder.build();

    // rewrite query params
    MultivaluedMap<String, String> params = req.getUriInfo().getQueryParameters();
    for( Map.Entry<String, List<String>> query : params.entrySet() ) {
      String key = query.getKey();
      if (QUERY_PARAMS.contains(key)) {
        String[] values = query.getValue().stream()
          .map(this::rewriteDatasetKey)
          .toArray(String[]::new);
        builder.replaceQueryParam(key, (Object[]) values);
      }
    }

    // rewrite path params
    Matcher m = LR_PATH.matcher(req.getUriInfo().getPath());
    if (m.find()) {
      // parsing cannot fail, we have a pattern
      int projectKey = Integer.parseInt(m.group(1));
      builder.replacePath(m.replaceFirst("dataset/" + latestRelease(projectKey)));
    }

    // change request
    URI rewritten = builder.build();
    if (!rewritten.equals(original)) {
      LOG.info("Rewrite URI {} to {}", original, rewritten);
      req.setRequestUri( rewritten );
    }
  }

  private String rewriteDatasetKey(String datasetKey) {
    if (StringUtils.hasContent(datasetKey) && datasetKey.endsWith(LATEST_RELEASE_SUFFIX)) {
      try {
        int projectKey = Integer.parseInt(datasetKey.substring(0, datasetKey.length() - 2));
        return latestRelease(projectKey).toString();
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(datasetKey + "is not a valid dataset key");
      }
    } else {
      return datasetKey;
    }
  }

  private Integer latestRelease(int projectKey) throws IllegalArgumentException {
    if (!isManaged(projectKey)) {
      // abort request! bad argument
      throw new IllegalArgumentException("Dataset " + projectKey + " is not a managed project");
    }
    Integer releaseKey = latest.get(projectKey);
    if (releaseKey == null) {
      throw new NotFoundException("Dataset " + projectKey + " was never released");
    }
    return releaseKey;
  }

  /**
   * @param projectKey a dataset key that is known to exist and point to a managed dataset
   * @return dataset key for the latest release of a project or null in case no release exists
   */
  private Integer lookupLatest(int projectKey) throws NotFoundException {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.latestRelease(projectKey);
    }
  }

  private boolean isManaged(int projectKey) throws IllegalArgumentException, NotFoundException {
    return managed.computeIfAbsent(projectKey, key -> {
      try (SqlSession session = factory.openSession()) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        Dataset d = dm.get(projectKey);
        if (d == null) {
          throw new NotFoundException("Dataset " + projectKey + " does not exist");
        }
        return d.getOrigin() == DatasetOrigin.MANAGED;
      }
    });
  }

  public void setSqlSessionFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }
}
