package life.catalogue.release.review;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
}
