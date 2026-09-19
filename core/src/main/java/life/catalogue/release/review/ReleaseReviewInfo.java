package life.catalogue.release.review;

import life.catalogue.api.vocab.DatasetOrigin;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The state of the agentic review of one release.
 *
 * There is no database table behind this. The review of a release is a pair of files in the release report
 * directory - {@code review.html} and the {@code review.json} sidecar this class serialises to - so a review
 * survives a redeploy, is served straight off the download host and is deleted together with the release
 * reports it sits next to.
 *
 * The first six properties describe the release and are always recomputed when read, so a moved report
 * directory or a newly published release in between cannot make them stale; only the rest is read back
 * from the sidecar.
 */
public class ReleaseReviewInfo {

  public enum Status {
    /** No review has ever been requested for this release. */
    NONE,
    /** A review job is queued or running. */
    RUNNING,
    /** The review finished and {@link #reportURI} points at its report. */
    FINISHED,
    /** The review failed, see {@link #error}. No report exists, so it can be requested again. */
    FAILED
  }

  private int releaseKey;
  private int projectKey;
  private DatasetOrigin origin;
  /**
   * The import attempt of the release, which is also the sub directory of the project's report folder
   * the report is written to.
   */
  private Integer attempt;
  /**
   * The public release just before this one with the same origin, or null if this is the first of its kind.
   */
  private Integer previousReleaseKey;
  private URI reportURI;

  private Status status = Status.NONE;
  /**
   * The key of the job that produced, or is producing, the review.
   */
  private UUID jobKey;
  /**
   * The Managed Agents session id, kept so a run can be inspected in the Anthropic console afterwards.
   */
  private String sessionId;
  private String model;
  private LocalDateTime started;
  private LocalDateTime finished;
  /**
   * What the session cost, as reported by the Managed Agents API. A free text amount, not a number, because
   * the API reports it with a currency.
   */
  private String listCost;
  /**
   * The failure message when {@link #status} is FAILED. Never carries a token or api key.
   */
  private String error;

  public int getReleaseKey() {
    return releaseKey;
  }

  public void setReleaseKey(int releaseKey) {
    this.releaseKey = releaseKey;
  }

  public int getProjectKey() {
    return projectKey;
  }

  public void setProjectKey(int projectKey) {
    this.projectKey = projectKey;
  }

  public DatasetOrigin getOrigin() {
    return origin;
  }

  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public Integer getPreviousReleaseKey() {
    return previousReleaseKey;
  }

  public void setPreviousReleaseKey(Integer previousReleaseKey) {
    this.previousReleaseKey = previousReleaseKey;
  }

  public URI getReportURI() {
    return reportURI;
  }

  public void setReportURI(URI reportURI) {
    this.reportURI = reportURI;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public UUID getJobKey() {
    return jobKey;
  }

  public void setJobKey(UUID jobKey) {
    this.jobKey = jobKey;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public LocalDateTime getStarted() {
    return started;
  }

  public void setStarted(LocalDateTime started) {
    this.started = started;
  }

  public LocalDateTime getFinished() {
    return finished;
  }

  public void setFinished(LocalDateTime finished) {
    this.finished = finished;
  }

  public String getListCost() {
    return listCost;
  }

  public void setListCost(String listCost) {
    this.listCost = listCost;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  @Override
  public String toString() {
    return "ReleaseReviewInfo{" + releaseKey + " " + status + " session=" + sessionId + '}';
  }
}
