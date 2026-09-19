package life.catalogue.release.review;

import life.catalogue.common.io.Resources;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReleaseReviewJobTest {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

  /**
   * Keeps the prompt template and the code that fills it in step. An unreplaced placeholder is a silent hole
   * in the instructions of a run that costs real money and is only noticed in the finished report.
   */
  @Test
  public void promptPlaceholdersAreAllSubstituted() throws Exception {
    String prompt = Resources.toString(ReleaseReviewJob.PROMPT_RESOURCE);
    var placeholders = PLACEHOLDER.matcher(prompt).results()
      .map(r -> r.group(1))
      .distinct()
      .sorted(Comparator.naturalOrder())
      .toList();
    assertEquals(List.of("apiURI", "attempt", "clbURI", "origin", "outputPath", "previousReleaseAlias",
      "previousReleaseKey", "projectKey", "releaseAlias", "releaseHistory", "releaseKey", "releaseReports",
      "reportsURI", "secretName", "sectorMetricsPath"), placeholders);
  }

  /**
   * The prompt has to name the mount path the job actually mounts the sector comparison at, and the output
   * path it later looks for. Both are constants here, so a change on one side must break this.
   */
  @Test
  public void promptNamesTheRealPaths() throws Exception {
    String prompt = Resources.toString(ReleaseReviewJob.PROMPT_RESOURCE);
    assertTrue(prompt.contains("{{sectorMetricsPath}}"));
    assertTrue(prompt.contains("{{outputPath}}"));
    assertEquals("/mnt/session/uploads/sector-metrics.json", ReleaseReviewJob.SANDBOX_METRICS_PATH);
    assertEquals("/mnt/session/outputs/review.html", ReleaseReviewJob.SANDBOX_OUTPUT_PATH);
    assertEquals("/sector-metrics.json", ReleaseReviewJob.MOUNT_PATH);
  }

  /**
   * The agent is told to write review.html, but a finished review must not be thrown away over a filename.
   */
  @Test
  public void picksTheReportFile() {
    var review = new ManagedAgentsClient.FileInfo("f1", "review.html");
    var other = new ManagedAgentsClient.FileInfo("f2", "notes.md");
    assertEquals(review, ReleaseReviewJob.pickReport(List.of(other, review)));
    // an absolute path is still the file we asked for
    var pathed = new ManagedAgentsClient.FileInfo("f3", "/mnt/session/outputs/review.html");
    assertEquals(pathed, ReleaseReviewJob.pickReport(List.of(pathed)));
    // any name ending in review.html is the file we asked for, however it is prefixed
    var prefixed = new ManagedAgentsClient.FileInfo("f4", "col-release-review.html");
    assertEquals(prefixed, ReleaseReviewJob.pickReport(List.of(other, prefixed)));
    // a single html under a completely different name is accepted rather than thrown away
    var single = new ManagedAgentsClient.FileInfo("f5", "col-report.html");
    assertEquals(single, ReleaseReviewJob.pickReport(List.of(other, single)));
    // two candidates under other names are ambiguous - do not guess
    assertNull(ReleaseReviewJob.pickReport(List.of(single, new ManagedAgentsClient.FileInfo("f6", "draft.html"))));
    assertNull(ReleaseReviewJob.pickReport(List.of()));
    assertNull(ReleaseReviewJob.pickReport(List.of(other)));
  }

  /**
   * This is a beta API: any status we have not seen must end the wait rather than keep the job polling until
   * its timeout, and a running one must never be mistaken for a finished one.
   */
  @Test
  public void unknownSessionStatusIsTerminal() {
    assertTrue(new ManagedAgentsClient.SessionState("idle", null).isTerminal());
    assertTrue(new ManagedAgentsClient.SessionState("completed", null).isTerminal());
    assertTrue(new ManagedAgentsClient.SessionState("failed", null).isTerminal());
    assertTrue(new ManagedAgentsClient.SessionState("something_new", null).isTerminal());
    assertTrue(new ManagedAgentsClient.SessionState("IDLE", null).isTerminal());

    assertNotNull(new ManagedAgentsClient.SessionState("running", null));
    assertTrue(!new ManagedAgentsClient.SessionState("running", null).isTerminal());
    assertTrue(!new ManagedAgentsClient.SessionState("RUNNING", null).isTerminal());
    assertTrue(!new ManagedAgentsClient.SessionState("queued", null).isTerminal());
    // a missing status is not an answer either way - keep polling until the timeout decides
    assertTrue(!new ManagedAgentsClient.SessionState(null, null).isTerminal());
  }

  /**
   * The api key is a header value and must never reach a log line or an admin config dump.
   */
  @Test
  public void configNeverPrintsTheApiKey() {
    var cfg = new AiReviewConfig();
    cfg.apiKey = "sk-ant-secret-value";
    cfg.agentId = "agent_1";
    cfg.environmentId = "env_1";
    cfg.vaultId = "vault_1";
    cfg.credentialId = "cred_1";
    assertTrue(!cfg.toString().contains("sk-ant-secret-value"));
    assertTrue(cfg.toString().contains("agent_1"));
  }
}
