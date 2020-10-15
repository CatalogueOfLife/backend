package life.catalogue.api.search;

import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;

/**
 * Represents a single suggestion coming back from the NameSuggestionService.
 */
public class NameUsageSuggestion {

  // The name matching the search phrase: an accepted name/synonym/bare name
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
    if (status == null || status.isBareName()) {
      return match + " (bare name)";
    } else if (status.isSynonym()) {
      return String.format("%s (%s of %s)", match, status.name().toLowerCase(), parentOrAcceptedName);
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append(match);

      boolean prov = status == TaxonomicStatus.PROVISIONALLY_ACCEPTED;
      boolean showRank = rank != null && (prov || rank.isSupraspecific());
      boolean showAcc = parentOrAcceptedName != null;

      if (showRank || prov || showAcc) {
        sb.append(" (");
        if (prov) {
          sb.append("prov.");
        }
        if (showRank) {
          if (prov) {
            sb.append(" ");
          }
          sb.append(rank.name().toLowerCase());
        }
        if (showAcc) {
          if (showRank || prov) {
            sb.append(" in ");
          }
          sb.append(parentOrAcceptedName);
        }
        sb.append(")");
      }
      return sb.toString();
    }
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
    return Objects.hash(parentOrAcceptedName, match, nomCode, rank, score, status, usageId, acceptedUsageId);
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
        && Objects.equals(acceptedUsageId, other.acceptedUsageId);
  }

}
