package life.catalogue.release.review;

import java.net.URI;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration of the agentic release review, run as an Anthropic Managed Agents session.
 *
 * Entirely optional - a deployment without an {@code ai:} block simply cannot request a review, which is the
 * normal state of dev and test. The values (api key, agent, environment, vault and credential ids) are created
 * once per environment and live in the private deploy repo, never here.
 *
 * Sits next to the job rather than in life.catalogue.config because it configures one feature of one job,
 * exactly like {@code GithubConfig} sits next to {@code GithubFeedback}.
 */
public class AiReviewConfig {

  @NotNull
  public URI api = URI.create("https://api.anthropic.com");

  /**
   * The Anthropic API key. Never logged and never serialised - see toString().
   */
  @NotNull
  public String apiKey;

  /**
   * The id of the pre-created agent that carries the model and system prompt.
   */
  @NotNull
  public String agentId;

  /**
   * The id of the pre-created sandbox environment. It must allow network access to {@link #apiHost} only.
   */
  @NotNull
  public String environmentId;

  /**
   * The id of the vault holding the ChecklistBank bot credential.
   */
  @NotNull
  public String vaultId;

  /**
   * The id of the credential inside {@link #vaultId} whose secret value is rotated to a freshly minted JWT
   * before every session.
   */
  @NotNull
  public String credentialId;

  /**
   * The environment variable the credential is injected as inside the sandbox.
   */
  @NotNull
  public String secretName = "CLB_TOKEN";

  /**
   * Recorded in the review sidecar for the report page. Purely descriptive - the actual model is a property
   * of the configured agent.
   */
  public String model = "claude-opus-5";

  /**
   * The session budget as a string amount of USD cents, passed through to the Managed Agents budget as
   * {@code max_list_cost}.
   */
  @NotNull
  public String maxCostCents = "2500";

  @Min(1)
  public int timeoutMinutes = 60;

  /**
   * How often the session status is polled, in seconds.
   */
  @Min(5)
  public int pollSeconds = 20;

  /**
   * The ChecklistBank API host the sandbox is allowed to reach, e.g. https://api.checklistbank.org.
   * Only used to render the prompt - the actual egress rules belong to the configured environment and vault,
   * which must allow exactly this host.
   *
   * Left out of the yaml it is filled from the server's own apiURI on startup, so a dev deployment can never
   * accidentally point its agent at the production API.
   */
  public URI apiURI;

  /**
   * The ChecklistBank UI the report links back to. Filled from the server's clbURI when not configured.
   */
  public URI clbURI;

  /**
   * The ChecklistBank username of the read-only review bot. A JWT is minted for this user for every run,
   * so no password is ever stored.
   */
  @NotNull
  public String botUser = "claude-review";

  public URI resolve(String path) {
    return api.resolve(path);
  }

  /**
   * Deliberately omits apiKey. Configs are logged on startup and dumped by the admin config endpoint.
   */
  @Override
  public String toString() {
    return "AiReviewConfig{api=" + api +
      ", agentId='" + agentId + '\'' +
      ", environmentId='" + environmentId + '\'' +
      ", vaultId='" + vaultId + '\'' +
      ", model='" + model + '\'' +
      ", maxCostCents='" + maxCostCents + '\'' +
      ", timeoutMinutes=" + timeoutMinutes +
      ", apiURI=" + apiURI +
      ", botUser='" + botUser + '\'' +
      '}';
  }
}
