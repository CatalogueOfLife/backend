package life.catalogue.dw.jersey.filter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import jakarta.annotation.Priority;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Makes a single dataset server - the CLB bundle - usable without knowing its dataset key by rewriting
 * keyless paths to the dataset scoped ones every resource already implements:
 *
 * <pre>
 *   /taxon/{id}      -&gt;  /dataset/{releaseKey}/taxon/{id}
 *   /tree            -&gt;  /dataset/{releaseKey}/tree
 *   /dataset         -&gt;  /dataset/{releaseKey}
 * </pre>
 *
 * No resource class is forked for this - they all still receive a {@code /dataset/{key}/...} path.
 * Already keyed paths and genuinely global roots ({@code /parser}, {@code /vocab}, {@code /version},
 * {@code /match/nameusage}, openapi, admin) are left alone.
 *
 * Like {@link DatasetKeyRewriteFilter} this is {@code @PreMatching} but deliberately NOT a {@code @Provider},
 * so the jersey package scan does not register it into the other apps. It runs before the alias filter, whose
 * work becomes a no-op once a numeric key is in the path.
 */
@PreMatching
@Priority(4000)
public class SingleDatasetRewriteFilter implements ContainerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(SingleDatasetRewriteFilter.class);
  private static final Set<String> METHODS = Set.of(HttpMethod.POST, HttpMethod.GET, HttpMethod.OPTIONS, HttpMethod.HEAD);

  /**
   * First path segments of the dataset scoped resources a bundle mounts, i.e. the roots of every
   * {@code @Path("/dataset/{key}/<root>")} resource registered by
   * {@code WsROServer.registerReadOnlyResources} plus the reconciliation resource the bundle adds.
   * SingleDatasetRewriteFilterTest derives the same set from the resource annotations and fails if it drifts.
   *
   * Note that {@code nameusage} and {@code vernacular} also exist as global resources. In a single dataset
   * server the rewrite deliberately shadows those - the dataset scoped variants are strictly richer.
   * {@code match} is absent on purpose: bulk matching is served by the global, already keyless
   * {@code /match/nameusage}.
   */
  @VisibleForTesting
  static final Set<String> DATASET_SCOPED_ROOTS = Set.of(
    "archive", "decision", "duplicate", "estimate", "import", "issues", "logo", "name", "nameusage",
    "patch", "reconcile", "reference", "sector", "source", "synonym", "taxon", "tree",
    "verbatim", "verbatimsource", "vernacular"
  );

  private static final String DATASET = "dataset";

  private final int releaseKey;

  public SingleDatasetRewriteFilter(int releaseKey) {
    this.releaseKey = releaseKey;
  }

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    if (!METHODS.contains(req.getMethod())) {
      return;
    }
    // the raw, still encoded path - taxon ids can be URLs, so never round trip them through a decode
    String rewritten = rewrite(req.getUriInfo().getPath(false), releaseKey);
    if (rewritten != null) {
      UriBuilder builder = req.getUriInfo().getRequestUriBuilder();
      final URI original = builder.build();
      builder.replacePath(rewritten);
      URI uri = builder.build();
      LOG.debug("Rewrite URI {} to {}", original, uri);
      req.setRequestUri(uri);
      req.setProperty(DatasetKeyRewriteFilter.ORIGINAL_URI_PROPERTY, original);
    }
  }

  /**
   * @param path the request path relative to the base URI, with or without a leading slash
   * @return the rewritten absolute path, or null if the path must be left alone
   */
  @VisibleForTesting
  static @Nullable String rewrite(String path, int releaseKey) {
    if (path == null) return null;
    String p = path;
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    if (p.isEmpty()) return null;

    int slash = p.indexOf('/');
    String root = slash < 0 ? p : p.substring(0, slash);
    String rest = slash < 0 ? "" : p.substring(slash + 1);

    if (DATASET.equals(root)) {
      // only the bare /dataset listing becomes the single dataset. /dataset/{key}/... stays as it is.
      return rest.isEmpty() ? "/dataset/" + releaseKey : null;
    }
    if (DATASET_SCOPED_ROOTS.contains(root)) {
      return "/dataset/" + releaseKey + "/" + p;
    }
    return null;
  }
}
