package life.catalogue.api.search;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

/**
 * Represents a single suggestion coming back from the NameSuggestionService.
 */
public class NameUsageSuggestion {

  // Whether this suggestion contains a scientific name or a vernacular name
  private boolean vernacularName;
  // The name matching the search phrase: an accepted name/synonym/bare name/vernacular name
  private String match;
  // The accepted name if the suggestion is anything but an accepted name, null otherwise
  private String acceptedName;
  private String usageId;
  private Rank rank;
  private TaxonomicStatus status;
  private NomCode nomCode;
  /*
   * Temporarily include the Elasticsearch score as well to see if it can help us fine-tune the auto-complete routines
   * (e.g. boost values).
   */
  private float score;

  /*
   * A simple construction of an actual suggestion created from the data in this {@code NameSuggestion} instance. Just an
   * example of how the drop-down list could be pupulated. Probably not actually useful because it's not multi-lingual.
   */
  public String getSuggestion() {
    if (vernacularName) {
      return String.format("%s (vernacular name of %s)", match, acceptedName);
    }
    if (status == null) {
      return match + " (nomen nudum)";
    }
    if (status.isSynonym()) {
      return String.format("%s (synonym of %s)", match, acceptedName);
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

  public String getAcceptedName() {
    return acceptedName;
  }

  public void setAcceptedName(String acceptedName) {
    this.acceptedName = acceptedName;
  }

  public String getUsageId() {
    return usageId;
  }

  public void setUsageId(String usageId) {
    this.usageId = usageId;
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
    return Objects.hash(acceptedName, match, nomCode, rank, score, status, usageId, vernacularName);
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
    return Objects.equals(acceptedName, other.acceptedName) && Objects.equals(match, other.match) && nomCode == other.nomCode
        && rank == other.rank && Float.floatToIntBits(score) == Float.floatToIntBits(other.score) && status == other.status
        && Objects.equals(usageId, other.usageId) && vernacularName == other.vernacularName;
  }

}
