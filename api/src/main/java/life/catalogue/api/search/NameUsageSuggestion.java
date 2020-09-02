package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;

/**
 * Represents a single suggestion coming back from the NameSuggestionService.
 */
public class NameUsageSuggestion {

  // Whether this suggestion contains a scientific name or a vernacular name
  private boolean vernacularName;
  // The name matching the search phrase: an accepted name/synonym/bare name/vernacular name
  private String match;
  // The parent taxon's accepted name if this is a suggestion for an accepted name, else the accepted name if this is a suggestion for
  // anyhing but an accepted name.
  private String parentOrAcceptedName;
  private String usageId;
  private String acceptedUsageId;
  private Rank rank;
  private TaxonomicStatus status;
  private NomCode nomCode;
  private float score;

  /**
   * Returns a single-line suggestion string. Could be used to populate a drop-down list. Probably not actually useful because it's not
   * multi-lingual.
   */
  public String getSuggestion() {
    if (vernacularName) {
      return String.format("%s (vernacular name of %s)", match, parentOrAcceptedName);
    } else if (status == null) {
      return match + " (bare name)";
    } else if (status.isSynonym()) {
      return String.format("%s (%s of %s)", match, status.name().toLowerCase(), parentOrAcceptedName);
    } else if (parentOrAcceptedName != null) { // not a kingdom or so
      return match + " (" + parentOrAcceptedName + ")";
    }
    return match;
  }

  @JsonIgnore
  public boolean isVernacularName() {
    return vernacularName;
  }

  public void setVernacularName(boolean vernacularName) {
    this.vernacularName = vernacularName;
  }

  public String getMatch() {
    return match;
  }

  public void setMatch(String match) {
    this.match = match;
  }

  public String getParentOrAcceptedName() {
    return parentOrAcceptedName;
  }

  public void setParentOrAcceptedName(String name) {
    this.parentOrAcceptedName = name;
  }

  public String getUsageId() {
    return usageId;
  }

  public void setUsageId(String usageId) {
    this.usageId = usageId;
  }

  public String getAcceptedUsageId() {
    return acceptedUsageId;
  }

  public void setAcceptedUsageId(String acceptedUsageId) {
    this.acceptedUsageId = acceptedUsageId;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public NomCode getNomCode() {
    return nomCode;
  }

  public void setNomCode(NomCode nomCode) {
    this.nomCode = nomCode;
  }

  public float getScore() {
    return score;
  }

  public void setScore(float score) {
    this.score = score;
  }

  @Override
  public int hashCode() {
    return Objects.hash(parentOrAcceptedName, match, nomCode, rank, score, status, usageId, acceptedUsageId, vernacularName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NameUsageSuggestion other = (NameUsageSuggestion) obj;
    return Objects.equals(parentOrAcceptedName, other.parentOrAcceptedName)
        && Objects.equals(match, other.match)
        && nomCode == other.nomCode
        && rank == other.rank
        && Float.floatToIntBits(score) == Float.floatToIntBits(other.score)
        && status == other.status
        && Objects.equals(usageId, other.usageId)
        && Objects.equals(acceptedUsageId, other.acceptedUsageId)
        && vernacularName == other.vernacularName;
  }

}
