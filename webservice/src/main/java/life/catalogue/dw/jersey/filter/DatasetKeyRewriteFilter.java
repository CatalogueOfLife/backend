package life.catalogue.dw.jersey.filter;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.text.StringUtils;
import life.catalogue.dao.DatasetInfoCache;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter that parses dataset keys given as path or query parameters
 * and rewrites them if needed to support conventions to refer to
 *  - the latest public release of a project: {projectKey}LR
 *  - the latest release candidate of a project, public or private: {projectKey}LRC
 *  - a specific release attempt of a project, public or private: {projectKey}R{attempt}
 *  - an annual version of COL: COL2021
 */
@PreMatching
public class DatasetKeyRewriteFilter implements ContainerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetKeyRewriteFilter.class);

  private static final String REL_PATTERN_STR = "(\\d+)(?:LRC?|R(\\d+))";
  private static final Pattern REL_PATTERN = Pattern.compile("^" + REL_PATTERN_STR + "$");
  private static final Pattern REL_PATH = Pattern.compile("dataset/" + REL_PATTERN_STR);
  private static final String COL_PREFIX = "COL";
  private static final Pattern COL_PATH = Pattern.compile("dataset/" + COL_PREFIX + "(20\\d\\d)", Pattern.CASE_INSENSITIVE);
  private static final Pattern COL_PATTERN = Pattern.compile("^" + COL_PREFIX + "(20\\d\\d)$");
  // all parameters that contain dataset keys and which we check if they need to be rewritten
  private static final Set<String> QUERY_PARAMS  = Set.of("datasetkey", "cataloguekey", "projectkey", "subjectdatasetkey", "hassourcedataset", "releasedfrom");
  private static final Set<String> METHODS  = Set.of(HttpMethod.GET, HttpMethod.OPTIONS, HttpMethod.HEAD);
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
    // apply only to simple read operations, not POST, PUT or DELETE
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
    Matcher m = REL_PATH.matcher(req.getUriInfo().getPath());
    if (m.find()) {
      Integer rkey = releaseKeyFromMatch(m);
      builder.replacePath(m.replaceFirst("dataset/" + rkey));
      if (m.group().endsWith("C")) {
        req.setProperty(ORIGINAL_DATASET_KEY_PROPERTY, m.group(1)+"LRC");
      } else {
        req.setProperty(ORIGINAL_DATASET_KEY_PROPERTY, m.group(1)+"LR");
      }
    } else {
      m = COL_PATH.matcher(req.getUriInfo().getPath());
      if (m.find()) {
        Integer rkey = releaseKeyFromYear(m);
        builder.replacePath(m.replaceFirst("dataset/" + rkey));
        req.setProperty(ORIGINAL_DATASET_KEY_PROPERTY, COL_PREFIX + m.group(1));
      }
    }

    // change request
    URI rewritten = builder.build();
    if (!rewritten.equals(original)) {
      LOG.debug("Rewrite URI {} to {}", original, rewritten);
      req.setRequestUri( rewritten );
      req.setProperty(ORIGINAL_URI_PROPERTY, original);
    }
  }

  public Optional<Integer> lookupDatasetKey(String datasetKey) {
    if (StringUtils.hasContent(datasetKey)) {
      Matcher m = REL_PATTERN.matcher(datasetKey);
      if (m.find()){
        return Optional.of(releaseKeyFromMatch(m));
      } else {
        m = COL_PATTERN.matcher(datasetKey);
        if (m.find()){
          return Optional.of(releaseKeyFromMatch(m));
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
    // candidate requested? (\\d+)(?:LRC?|R(\\d+))$
    if (m.group().endsWith("C")) {
      releaseKey = cache.getLatestReleaseCandidate(projectKey);
    } else if (m.group().endsWith("R")) {
      releaseKey = cache.getLatestRelease(projectKey);
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

  private Integer releaseKeyFromYear(Matcher m) {
    // parsing cannot fail, we have a pattern
    int year = Integer.parseInt(m.group(1));
    Integer releaseKey = cache.getColAnnualRelease(year);
    if (releaseKey == null) {
      throw new NotFoundException("COL Annual Checklist " + year + " was never released");
    }
    return releaseKey;
  }
}
