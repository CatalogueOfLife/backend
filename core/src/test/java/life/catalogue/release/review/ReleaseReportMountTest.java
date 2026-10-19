package life.catalogue.release.review;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.http.ContentType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The release reports a review mounts, against a report directory laid out as the release job leaves it.
 */
public class ReleaseReportMountTest {
  private File dir;
  private final Map<String, byte[]> uploaded = new LinkedHashMap<>();
  private final List<ManagedAgentsClient.Mount> mounts = new ArrayList<>();
  private final StringBuilder reports = new StringBuilder();

  /** records uploads instead of sending them */
  private final ManagedAgentsClient client = new ManagedAgentsClient(new AiReviewConfig(), null) {
    @Override
    public String uploadFile(String filename, byte[] content, ContentType type) {
      uploaded.put(filename, content);
      return "file_" + uploaded.size();
    }
  };

  @Before
  public void init() throws Exception {
    dir = Files.createTempDirectory("release-reports").toFile();
  }

  @After
  public void cleanup() throws Exception {
    FileUtils.deleteDirectory(dir);
  }

  private void writeIdReports(Map<String, String> entries) throws Exception {
    // a zip archive, named .gz like the IdProvider names it
    try (var zip = new ZipOutputStream(new FileOutputStream(new File(dir, ReleaseReviewJob.ID_REPORTS_FILE)))) {
      for (var e : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
  }

  @Test
  public void mountsTheIdReportsAsTextFiles() throws Exception {
    var entries = new LinkedHashMap<String, String>();
    entries.put("deleted.tsv", "4T\tphylum\taccepted\tKamptozoa\t\n");
    entries.put("created.tsv", "");
    entries.put("unstable.txt", "Abacetus popovi\n - Abacetus popovi Straneo, 1958 [SYNONYM SPECIES 2328:8HXJ nidx=null]\n");
    writeIdReports(entries);

    ReleaseReviewJob.mountIdReports(client, mounts, reports, "the previous release", dir, ReleaseReviewJob.PREFIX_PREVIOUS);

    assertEquals(List.of("previous-id-reports-deleted.tsv", "previous-id-reports-unstable.txt"), List.copyOf(uploaded.keySet()));
    assertEquals(List.of("/previous-id-reports/deleted.tsv", "/previous-id-reports/unstable.txt"),
      mounts.stream().map(ManagedAgentsClient.Mount::mountPath).toList());
    assertEquals("4T\tphylum\taccepted\tKamptozoa\t\n", new String(uploaded.get("previous-id-reports-deleted.tsv"), StandardCharsets.UTF_8));
    assertEquals("- `/mnt/session/uploads/previous-id-reports/` - the ID reports of the previous release: "
      + "deleted.tsv, unstable.txt; not mounted: created.tsv (empty)\n", reports.toString());
  }

  @Test
  public void mountsADigestOfTheLogNotTheLogItself() throws Exception {
    try (Writer w = new OutputStreamWriter(new GZIPOutputStream(
      new FileOutputStream(new File(dir, ReleaseReviewJob.LOG_FILE))), StandardCharsets.UTF_8)) {
      w.write("2026-09-11 04:19:46,086 INFO  BackgroundJob                    Started ProjectRelease job 0cbe82fb\n");
    }

    ReleaseReviewJob.mountLogDigest(client, mounts, reports, "this release", dir, "");

    assertEquals(List.of("job-log-digest.md"), List.copyOf(uploaded.keySet()));
    assertEquals("/job-log-digest.md", mounts.get(0).mountPath());
    String digest = new String(uploaded.get("job-log-digest.md"), StandardCharsets.UTF_8);
    assertTrue(digest.startsWith("# Release job log digest"));
    assertTrue(digest.contains("Started ProjectRelease job 0cbe82fb"));
    assertEquals("- `/mnt/session/uploads/job-log-digest.md` - the job log digest of this release\n", reports.toString());
  }

  /**
   * Extended releases have been seen without a job log, and a first release has no previous one at all.
   * Neither may fail the review - the agent is told what is missing instead.
   */
  @Test
  public void listsMissingReportsInsteadOfFailing() throws Exception {
    ReleaseReviewJob.mountLogDigest(client, mounts, reports, "this release", dir, "");
    ReleaseReviewJob.mountIdReports(client, mounts, reports, "the previous release", null, ReleaseReviewJob.PREFIX_PREVIOUS);

    assertTrue(uploaded.isEmpty());
    assertTrue(mounts.isEmpty());
    assertEquals("- **not available:** the job log of this release - there is no job.log.gz in its report directory\n"
      + "- **not available:** the ID reports of the previous release - there is no id-reports.gz in its report directory\n",
      reports.toString());
    assertFalse(reports.toString().contains(dir.getAbsolutePath()));
  }

  /** serves files from a map, by their last path segment, instead of download.gbif.org and api.gbif.org */
  private static GbifImpactReports gbif(Map<String, String> files) {
    return new GbifImpactReports(URI.create("https://download.gbif.org/catalogueoflife"),
      URI.create("https://api.gbif.org/v2/species/match/metadata?checklistKey=7ddf754f"), null) {
      @Override
      protected byte[] get(URI uri) throws IOException {
        String filename = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
        if (!files.containsKey(filename)) {
          throw new IOException("HTTP 404 for " + filename);
        }
        return files.get(filename).getBytes(StandardCharsets.UTF_8);
      }
    };
  }

  private static String indexEntry(String key, String scope, String created) {
    return "{\"name\": \"" + scope + " - COL26.10 XR (" + key + ")\", \"description\": \"" + scope
      + " having more than 10 occurrences\", \"created\": \"" + created + "\", \"colVersion\": \"XCOL\", "
      + "\"colKey\": \"" + key + "\", \"filename\": \"report-" + key + "-" + scope + "-" + created + ".txt\"}";
  }

  /**
   * Only the reports of the release under review, and of a scope GBIF generated twice only the newest.
   */
  @Test
  public void mountsTheNewestGbifImpactReportOfEachScope() throws Exception {
    var files = new LinkedHashMap<String, String>();
    files.put("index.json", "{\"reports\": [" + String.join(",",
      indexEntry("316691", "fossils", "2026-10-18"),
      indexEntry("316691", "alltaxa", "2026-10-17"),
      indexEntry("316691", "alltaxa", "2026-10-18"),
      indexEntry("316551", "alltaxa", "2026-10-11"),
      indexEntry("316691", "../../etc", "2026-10-18")) + "]}");
    files.put("report-316691-alltaxa-2026-10-18.txt", "count\tverbatim_taxonID\n1245\tnull\n");
    files.put("report-316691-alltaxa-2026-10-17.txt", "stale");
    files.put("metadata", "{\"created\": \"2026-07-18T07:34:50.904Z\", \"mainIndex\": {\"clbDatasetKey\": \"315557\", "
      + "\"datasetKey\": \"7ddf754f-d193-4cc9-b351-99906754a03b\", \"datasetAlias\": \"COL26.6 XR\"}}");

    ReleaseReviewJob.mountGbifImpact(client, mounts, reports, gbif(files), 316691);

    assertEquals(List.of("gbif-impact-report-316691-alltaxa-2026-10-18.txt"), List.copyOf(uploaded.keySet()));
    assertEquals("/gbif-impact/report-316691-alltaxa-2026-10-18.txt", mounts.get(0).mountPath());
    assertEquals("count\tverbatim_taxonID\n1245\tnull\n",
      new String(uploaded.get("gbif-impact-report-316691-alltaxa-2026-10-18.txt"), StandardCharsets.UTF_8));
    assertEquals("- **current** is release `315557` (COL26.6 XR), the release GBIF matches occurrences against in production, "
      + "its matching index built 2026-07-18T07:34:50.904Z\n"
      + "- `/mnt/session/uploads/gbif-impact/report-316691-alltaxa-2026-10-18.txt` - alltaxa - COL26.10 XR (316691): "
      + "alltaxa having more than 10 occurrences\n"
      + "- **not available:** `report-316691-fossils-2026-10-18.txt` - HTTP 404 for report-316691-fossils-2026-10-18.txt\n",
      reports.toString());
  }

  /**
   * Not knowing which release "current" is still mounts the reports.
   */
  @Test
  public void mountsGbifImpactReportsWithoutKnowingCurrent() throws Exception {
    ReleaseReviewJob.mountGbifImpact(client, mounts, reports, gbif(Map.of(
      "index.json", "{\"reports\": [" + indexEntry("316691", "alltaxa", "2026-10-18") + "]}",
      "report-316691-alltaxa-2026-10-18.txt", "count\n")), 316691);

    assertEquals(1, mounts.size());
    assertTrue(reports.toString().startsWith(
      "- **not known:** which release GBIF matches against in production: HTTP 404 for metadata\n"));
  }

  /**
   * GBIF builds the reports some time after the release, and its host may be down. Neither fails the review.
   */
  @Test
  public void listsMissingGbifImpactReportsInsteadOfFailing() throws Exception {
    ReleaseReviewJob.mountGbifImpact(client, mounts, reports,
      gbif(Map.of("index.json", "{\"reports\": [" + indexEntry("316551", "alltaxa", "2026-10-11") + "]}")), 316691);
    ReleaseReviewJob.mountGbifImpact(client, mounts, reports, gbif(Map.of()), 316691);
    ReleaseReviewJob.mountGbifImpact(client, mounts, reports, null, 316691);

    assertTrue(uploaded.isEmpty());
    assertTrue(mounts.isEmpty());
    assertEquals("- **not available:** GBIF has not published an impact report for release 316691 yet\n"
      + "- **not available:** the GBIF impact reports - their index could not be read: HTTP 404 for index.json\n"
      + "- **not available:** no location of the GBIF impact reports is configured\n",
      reports.toString());
  }
}
