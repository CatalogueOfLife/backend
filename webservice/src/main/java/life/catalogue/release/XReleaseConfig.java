package life.catalogue.release;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.Issue;

import java.util.*;

import javax.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.gbif.nameparser.api.Rank;

public class XReleaseConfig {

  /**
   * XRelease alias with project variables allowed to be applied to a release
   */
  public String alias;

  /**
   * XRelease title with project variables allowed to be applied to a release
   */
  public String title;

  /**
   * XRelease version with project variables allowed to be applied to a release
   */
  public String version;

  /**
   * XRelease description with project variables allowed to be applied to a release
   */
  public String description;

  /**
   * Selected list of CLB dataset keys to exclude as sectors being created for aboves source publisher keys
   */
  public Set<Integer> sourceDatasetExclusion;

  /**
   * An optional incertae sedis taxon that should be used by the release to place all names with unknown classifications.
   * The taxon will be created if necessary, but an existing name will be preferred.
   * A classification can be given to specify a non root placement.
   */
  @Valid
  @Nullable
  public SimpleNameClassified<SimpleName> incertaeSedis;

  /**
   * If true remove empty genera created during the xrelease only.
   */
  @Valid
  public boolean removeEmptyGenera = true;

  /**
   * If true algorithmic detecting and grouping of basionyms is executed.
   */
  @Valid
  public boolean homotypicConsolidation = true;

  @Min(1)
  public int homotypicConsolidationThreads = 4;

  /**
   * An optional set of issues that if found on the usage or name will trigger the exclusion of the usage in the merge syncs.
   */
  @Valid
  @NotNull
  public Set<Issue> issueExclusion = new HashSet<>();

  /**
   * List of scientific names that are globally blocked from any source.
   * Names are case insensitive and are allowed to be canonical to match all authorships or with a single specific authorship!
   */
  @NotNull
  @Valid
  public Set<String> blockedNames = new HashSet<>();

  /**
   * List of regular expression patterns for scientific names that are globally blocked from any source.
   * Patterns are case insensitive and must not be anchored at the front. Any match will block the name.
   */
  @NotNull
  @Valid
  public Set<String> blockedNamePatterns = new HashSet<>();

  /**
   * List of uninomial taxa known to be unique and for which there should never be more than 1 accepted version.
   * Canonical names without authorship are listed by their rank.
   */
  @NotNull
  @Valid
  public Map<Rank, Set<String>> enforceUnique = new HashMap<>();

  /**
   * Checks the homonymExclusion list to see if this combination should be excluded.
   * @return true if the name with the given parent should be excluded
   */
  public boolean enforceUnique(Name sn) {
    return enforceUnique.containsKey(sn.getRank()) && enforceUnique.get(sn.getRank()).contains(sn.getScientificName());
  }

  /**
   * List of epithets, organised by families, which should be ignored during the automated basionym grouping/detection.
   */
  @NotNull
  @Valid
  public Map<String, Set<String>> basionymExclusions = new HashMap<>();

  /**
   * List of additional editorial decisions which can override existing decisions from the base.
   */
  @NotNull
  @Valid
  public Map<Integer, List<EditorialDecision>> decisions = new HashMap<>();
}
