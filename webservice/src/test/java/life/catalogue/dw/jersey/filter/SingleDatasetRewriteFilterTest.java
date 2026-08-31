package life.catalogue.dw.jersey.filter;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.reflect.ClassPath;

import jakarta.ws.rs.Path;

import static life.catalogue.dw.jersey.filter.SingleDatasetRewriteFilter.DATASET_SCOPED_ROOTS;
import static life.catalogue.dw.jersey.filter.SingleDatasetRewriteFilter.rewrite;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SingleDatasetRewriteFilterTest {
  static final int KEY = 3287;

  @Test
  public void keylessPathsAreRewritten() {
    assertEquals("/dataset/3287/taxon/ABCD", rewrite("taxon/ABCD", KEY));
    assertEquals("/dataset/3287/taxon/ABCD", rewrite("/taxon/ABCD", KEY));
    assertEquals("/dataset/3287/taxon/ABCD/info", rewrite("taxon/ABCD/info", KEY));
    assertEquals("/dataset/3287/tree", rewrite("tree", KEY));
    assertEquals("/dataset/3287/tree/N/children", rewrite("tree/N/children", KEY));
    assertEquals("/dataset/3287/nameusage/search", rewrite("nameusage/search", KEY));
    assertEquals("/dataset/3287/reconcile", rewrite("reconcile", KEY));
    assertEquals("/dataset/3287/reconcile/extend/propose", rewrite("reconcile/extend/propose", KEY));
    assertEquals("/dataset/3287/vernacular", rewrite("vernacular", KEY));
    assertEquals("/dataset/3287/sector/publisher", rewrite("sector/publisher", KEY));
  }

  @Test
  public void encodedIdsSurvive() {
    // taxon ids can be URLs, so the raw path must be passed through untouched
    assertEquals("/dataset/3287/taxon/http%3A%2F%2Fx.org%2F1", rewrite("taxon/http%3A%2F%2Fx.org%2F1", KEY));
  }

  @Test
  public void bareDatasetBecomesTheSingleRelease() {
    assertEquals("/dataset/3287", rewrite("dataset", KEY));
    assertEquals("/dataset/3287", rewrite("/dataset", KEY));
    assertEquals("/dataset/3287", rewrite("dataset/", KEY));
  }

  @Test
  public void keyedAndGlobalPathsAreLeftAlone() {
    // already keyed
    assertNull(rewrite("dataset/3287/taxon/ABCD", KEY));
    assertNull(rewrite("dataset/3LR/taxon/ABCD", KEY));
    assertNull(rewrite("dataset/1000", KEY));
    // genuinely global
    assertNull(rewrite("parser/name", KEY));
    assertNull(rewrite("vocab/rank", KEY));
    assertNull(rewrite("version", KEY));
    assertNull(rewrite("nidx/match", KEY));
    assertNull(rewrite("match/nameusage", KEY));
    assertNull(rewrite("export/1234", KEY));
    assertNull(rewrite("job/types", KEY));
    assertNull(rewrite("openapi", KEY));
    // nothing to do
    assertNull(rewrite("", KEY));
    assertNull(rewrite("/", KEY));
    assertNull(rewrite(null, KEY));
  }

  /**
   * Dataset scoped resources that exist in the code but are NOT mounted by a bundle, so the filter must not
   * rewrite their root either. Together with {@link #allowlistMatchesTheResources()} this makes adding a new
   * dataset scoped resource a deliberate decision instead of a silent 404.
   */
  static final Set<String> NOT_BUNDLED = Set.of(
    "breakdown",  // DatasetBreakdownResource, not in the read only set
    "diff",       // DatasetDiffResource
    "editor",     // DatasetEditorResource, a write resource
    "export",     // DatasetExportResource, only the global /export is mounted
    "legacy",     // LegacyWebserviceResource
    "match",      // served globally and already keyless by FixedNameUsageMatchingResource
    "reviewer",   // DatasetReviewerResource, a write resource
    "taxalign"    // DatasetTaxDiffResource
  );

  @Test
  public void allowlistMatchesTheResources() throws IOException {
    Set<String> found = datasetScopedRoots();
    // every allowlisted root is really served by a /dataset/{key}/<root> resource
    Set<String> unknown = new TreeSet<>(DATASET_SCOPED_ROOTS);
    unknown.removeAll(found);
    assertEquals("Allowlisted roots without a matching resource", Set.of(), unknown);
    // and every dataset scoped resource is either mounted or explicitly excluded
    Set<String> undecided = new TreeSet<>(found);
    undecided.removeAll(DATASET_SCOPED_ROOTS);
    undecided.removeAll(NOT_BUNDLED);
    assertEquals("New dataset scoped resources - add them to DATASET_SCOPED_ROOTS or to NOT_BUNDLED",
      Set.of(), undecided);
  }

  private static final Pattern DS_PATH = Pattern.compile("^/?dataset/\\{key}/([^/{]+)");

  private static Set<String> datasetScopedRoots() throws IOException {
    Set<String> roots = new TreeSet<>();
    var cp = ClassPath.from(SingleDatasetRewriteFilter.class.getClassLoader());
    for (String pkg : new String[]{"life.catalogue.resources.dataset", "life.catalogue.resources.legacy",
                                   "life.catalogue.resources.matching.openrefine", "life.catalogue.resources"}) {
      for (ClassPath.ClassInfo info : cp.getTopLevelClasses(pkg)) {
        Path p = info.load().getAnnotation(Path.class);
        if (p != null) {
          Matcher m = DS_PATH.matcher(p.value());
          if (m.find()) {
            roots.add(m.group(1));
          }
        }
      }
    }
    return roots;
  }
}
