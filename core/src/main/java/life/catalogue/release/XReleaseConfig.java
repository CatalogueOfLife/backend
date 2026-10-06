package life.catalogue.release;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;

import org.gbif.nameparser.api.Rank;

import java.util.*;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The name and issue exclusions issueExclusion, blockedNames and blockedNamePatterns moved into sector profiles,
 * see docs/SECTOR-SETTINGS.md. Config files written before still carry them, so they are explicitly ignored
 * rather than failing a strict read.
 */
@JsonIgnoreProperties({"issueExclusion", "blockedNames", "blockedNamePatterns"})
public class XReleaseConfig extends ProjectReleaseConfig {

  /**
   * If false failing sector syncs will be swallowed, logged and the release will continue with the rest of the sectors.
   */
  public boolean failOnSyncErrors = true;

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
   * If true algorithmic detects and synonymizes (sub)species misspellings per family.
   */
  @Valid
  public boolean misspellingConsolidation = true;

  /**
   * Flag exact same, accepted names but with different authors as provisionally accepted.
   */
  @Valid
  public boolean flagDuplicatesAsProvisional = true;

  /**
   * If true algorithmic detecting and grouping of basionyms is executed.
   */
  @Valid
  public boolean homotypicConsolidation = true;

  @Min(1)
  public int homotypicConsolidationThreads = 4;

  /**
   * List of uninomial taxa known to be unique and for which there should never be more than 1 accepted version.
   * Canonical names without authorship are listed by their rank.
   */
  @NotNull
  @Valid
  public Map<Rank, Set<String>> enforceUnique = new HashMap<>();

  /**
   * Checks if the canonical name is known to be unique and for which there should never be more than 1 accepted version.
   */
  public boolean enforceUnique(Name sn) {
    return enforceUnique.containsKey(sn.getRank()) && enforceUnique.get(sn.getRank()).contains(sn.getScientificName());
  }

  /**
   * Accepted taxa of the project (the merge sync target) that must be shielded from all merge syncs.
   * Canonical scientificNames (without authorship) are listed by their rank.
   * A protected taxon and its entire subtree of descendants will not receive any updates and no new
   * names or other data will be inserted anywhere within the group.
   * On release startup each protected taxon is verified to exist, be accepted and unique - the release
   * fails otherwise. A warning is logged for every protected group.
   */
  @NotNull
  @Valid
  public Map<Rank, Set<String>> protectedGroups = new HashMap<>();

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
