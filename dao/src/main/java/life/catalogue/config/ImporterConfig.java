package life.catalogue.config;

import java.util.List;

import javax.annotation.Nullable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 *
 */
@SuppressWarnings("PublicField")
public class ImporterConfig {

  @Min(1)
  public int batchSize = 10000;
  

  /**
   * Github API access token to use when downloading data from github.com URLs.
   */
  public String githubToken;
  public String githubTokenGeoff;
  /**
   * Github shared secret to encrypt webhook messages
   */
  @Nullable
  public String githubHookSecret;

  /**
   * Number of seconds to wait after an import has finished before the job is done.
   * This is really for testing only to avoid import jobs to complete before the assertions have run.
   * Keep the default of zero for production environments!
   */
  public int wait = 0;

  /**
   * Timeout in seconds for the optional import/validation completion callback POST.
   * See https://github.com/CatalogueOfLife/backend/issues/1552
   */
  @Min(1)
  public int callbackTimeout = 10;

  /**
   * The reference checklists accepted genera are checked against for a placement in a taxonomic group none of them
   * knows them in, flagged as PLACEMENT_CONFLICT. Their genera are pooled, so a genus any reference knows is no conflict.
   * Same syntax as the ADD_IDENTIFIERS_FROM setting: a dataset key or a project key with an LR or LXR suffix for its
   * latest public (extended) release. Defaults to the COL base release and IRMNG, empty disables the check.
   * See https://github.com/CatalogueOfLife/backend/issues/1608
   */
  @NotNull
  public List<String> placementReferences = List.of("3LR", "2007");

  @Valid
  @NotNull
  public ContinuousImportConfig continuous = new ContinuousImportConfig();

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return false;
  }
}
