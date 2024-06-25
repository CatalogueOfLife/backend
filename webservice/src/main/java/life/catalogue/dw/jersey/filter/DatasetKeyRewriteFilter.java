package life.catalogue.dw.jersey.filter;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.text.StringUtils;
import life.catalogue.dao.DatasetInfoCache;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Filter that parses dataset keys given as path or query parameters
 * and rewrites them if needed to support conventions to refer to
 *  - the latest public release of a project: {projectKey}LR
 *  - the latest private release candidate of a project: {projectKey}LRC
 *  - the latest public extended release of a project: {projectKey}LXR
 *  - the latest private extended release candidate of a project: {projectKey}LXRC
 *  - a specific release attempt of a project, public or private: {projectKey}R{attempt}
 *  - an annual version of COL: COL2021
 *  - a GBIF UUID key: gbif-a66592b8-63a8-4c2d-9471-e58ddb2c0559
 */
@PreMatching
public class DatasetKeyRewriteFilter implements ContainerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetKeyRewriteFilter.class);

  private static final String REL_PATTERN_STR = "(\\d+)(?:LX?RC?|R(\\d+))";
  private static final Pattern REL_PATTERN = Pattern.compile("^" + REL_PATTERN_STR + "$");
  private static final Pattern REL_PATH = Pattern.compile("dataset/" + REL_PATTERN_STR);

  private static final String COL_ALIAS = "(X?COL)(?:20)?(\\d\\d)(?:\\.(1?\\d))?";
  private static final Pattern COL_PATTERN = Pattern.compile("^" + COL_ALIAS + "$");
  private static final Pattern COL_PATH = Pattern.compile("dataset/" + COL_ALIAS, Pattern.CASE_INSENSITIVE);

  private static final String GBIF_PATTERN_STR = "gbif-([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})";
  @VisibleForTesting
  static final Pattern GBIF_PATTERN = Pattern.compile("^" + GBIF_PATTERN_STR + "$", Pattern.CASE_INSENSITIVE);
  private static final Pattern GBIF_PATH = Pattern.compile("dataset/" + GBIF_PATTERN_STR, Pattern.CASE_INSENSITIVE);
  // all parameters that contain dataset keys and which we check if they need to be rewritten
  private static final Set<String> QUERY_PARAMS  = Set.of("datasetkey", "cataloguekey", "projectkey", "subjectdatasetkey", "sourcedatasetkey", "hassourcedataset", "releasedfrom");
  private static final Set<String> METHODS  = Set.of(HttpMethod.POST, HttpMethod.GET, HttpMethod.OPTIONS, HttpMethod.HEAD);
  public static final String ORIGINAL_URI_PROPERTY = "originalRequestURI";
  public static final String ORIGINAL_DATASET_KEY_PROPERTY = "originalDatasetKey";

  private final LatestDatasetKeyCache cache;

  public DatasetKeyRewriteFilter(LatestDatasetKeyCache cache) {
    this.cache = cache;
  }

  static String normParam(String param) {
    return param.toLowerCase().replaceAll("_", "").trim();
  }

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    // apply only to simple read operations and POST which is used to trigger jobs. But not to PUT or DELETE
    if (!METHODS.contains(req.getMethod())) {
      return;
    }

    UriBuilder builder = req.getUriInfo().getRequestUriBuilder();
    final URI original = builder.build();

    // rewrite query params
    MultivaluedMap<String, String> params = req.getUriInfo().getQueryParameters();
    for( Map.Entry<String, List<String>> query : params.entrySet() ) {
      if (QUERY_PARAMS.contains(normParam(query.getKey()))) {
        Object[] values = query.getValue().stream()
          .map(this::rewriteDatasetKey)
          .toArray(String[]::new);
        if (!CollectionUtils.equals(query.getValue(), values)) {
          builder.replaceQueryParam(query.getKey(), values);
        }
      }
    }

    // rewrite path params
    lookupPath(req.getUriInfo().getPath(), builder, req);

    // change request
    URI rewritten = builder.build();
    if (!rewritten.equals(original)) {
      LOG.debug("Rewrite URI {} to {}", original, rewritten);
      req.setRequestUri( rewritten );
      req.setProperty(ORIGINAL_URI_PROPERTY, original);
    }
  }

  public Optional<Integer> lookupPath(String path) {
    return lookupPath(path, null, null);
  }

  private Optional<Integer> lookupPath(String path, @Nullable UriBuilder builder, @Nullable ContainerRequestContext req){
    Integer key = null;
    Matcher m = REL_PATH.matcher(path);
    if (m.find()) {
      key = releaseKeyFromMatch(m);
      if (builder != null && req != null) {
        builder.replacePath(m.replaceFirst("dataset/" + key));
        if (m.group().endsWith("C")) {
          req.setProperty(ORIGINAL_DATASET_KEY_PROPERTY, m.group(1)+"LRC");
        } else {
          req.setProperty(ORIGINAL_DATASET_KEY_PROPERTY, m.group(1)+"LR");
        }
      }

    } else {
      m = COL_PATH.matcher(path);
      if (m.find()) {
        key = releaseKeyFromAlias(m);
        if (builder != null && req != null) {
          builder.replacePath(m.replaceFirst("dataset/" + key));
          req.setProperty(ORIGINAL_DATASET_KEY_PROPERTY, m.group(1) + m.group(2));
        }

      } else {
        m = GBIF_PATH.matcher(path);
        if (m.find()) {
          key = datasetKeyFromGBIF(m.group(1));
          if (builder != null && req != null) {
            builder.replacePath(m.replaceFirst("dataset/" + key));
            req.setProperty(ORIGINAL_DATASET_KEY_PROPERTY, m.group());
          }
        }
      }
    }
    return Optional.ofNullable(key);
  }

  private Integer datasetKeyFromGBIF(String uuid) {
    UUID gbif = UUID.fromString(uuid);
    Integer datasetKey = cache.getDatasetKeyByGbif(gbif);
    if (datasetKey == null) {
      throw new NotFoundException("GBIF dataset key " + uuid + " not found");
    }
    return datasetKey;
  }

  public Optional<Integer> lookupDatasetKey(String datasetKey) {
    if (StringUtils.hasContent(datasetKey)) {
      Matcher m = REL_PATTERN.matcher(datasetKey);
      if (m.find()){
        return Optional.of(releaseKeyFromMatch(m));
      } else {
        m = COL_PATTERN.matcher(datasetKey);
        if (m.find()){
          return Optional.of(releaseKeyFromAlias(m));
        } else {
          m = GBIF_PATTERN.matcher(datasetKey);
          if (m.find()){
            return Optional.of(datasetKeyFromGBIF(m.group(1)));
          }
        }
      }
    }
    return Optional.empty();
  }

  private String rewriteDatasetKey(String datasetKey) {
    return lookupDatasetKey(datasetKey)
      .map(Object::toString)
      .orElse(datasetKey);
  }

  private Integer releaseKeyFromMatch(Matcher m) {
    // parsing cannot fail, we have a pattern
    int projectKey = Integer.parseInt(m.group(1));

    if (DatasetInfoCache.CACHE.info(projectKey).origin != DatasetOrigin.PROJECT) {
      // abort request! bad argument
      throw new IllegalArgumentException("Dataset " + projectKey + " is not a project");
    }

    Integer releaseKey;
    // candidate requested? (\\d+)(?:LX?RC?|R(\\d+))$
    final boolean extended = m.group().contains("X");
    if (m.group().endsWith("RC")) {
      releaseKey = cache.getLatestReleaseCandidate(projectKey, extended);
    } else if (m.group().endsWith("R")) {
      releaseKey = cache.getLatestRelease(projectKey, extended);
    } else {
      // parsing cannot fail, we have a pattern
      int attempt = Integer.parseInt(m.group(2));
      releaseKey = cache.getReleaseByAttempt(projectKey, attempt);
    }

    if (releaseKey == null) {
      throw new NotFoundException("Dataset " + projectKey + " was never released");
    }
    return releaseKey;
  }

  private Integer releaseKeyFromAlias(Matcher m) {
    final boolean extended = m.group(1).startsWith("X");
    // parsing cannot fail, we have a pattern
    int year = Integer.parseInt(m.group(2));
    // month is optional
    Integer releaseKey;
    if (m.group(3) == null) {
      releaseKey = cache.getColRelease(year, extended);
    } else {
      releaseKey = cache.getColRelease(year, Integer.parseInt(m.group(3)), extended);
    }
    if (releaseKey == null) {
      String alias = m.group(1)+m.group(2);
      if (m.group(3) != null) {
        alias = alias + "." + m.group(3);
      }
      throw new NotFoundException( alias + " was never released");
    }
    return releaseKey;
  }
}
