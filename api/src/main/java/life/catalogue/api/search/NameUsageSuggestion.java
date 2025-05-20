package life.catalogue.api.search;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a single suggestion coming back from the NameSuggestionService.
 */
public class NameUsageSuggestion {

  // The name matching the search phrase: an accepted name/synonym/bare name
  private String match;
  // The classification context to report in the suggestion hint.
  // For accepted names this is the first taxon above genus level, mostly the family.
  // For synonyms it is the accepted name
  private String context;
  private String usageId;
  private String nameId;
  private String acceptedUsageId;
  private Rank rank;
  private TaxonomicStatus status;
  private NomCode nomCode;
  private float score;
  private TaxGroup group;

  /**
   * Returns a single-line suggestion string. Could be used to populate a drop-down list.
   */
  public String getSuggestion() {
    if (status == null || status.isBareName()) {
      return match + " (bare name)";
    } else if (status.isSynonym()) {
      return String.format("%s (%s of %s)", match, status.name().toLowerCase(), context);
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append(match);

      boolean prov = status == TaxonomicStatus.PROVISIONALLY_ACCEPTED;
      boolean showRank = rank != null && (prov || rank.isSupraspecific());
      boolean showAcc = context != null;

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
          sb.append(context);
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

  @JsonIgnore
  public String getContext() {
    return context;
  }

  public void setContext(String name) {
    this.context = name;
  }

  public TaxGroup getGroup() {
    return group;
  }

  public void setGroup(TaxGroup group) {
    this.group = group;
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

  public String getNameId() {
    return nameId;
  }

  public void setNameId(String nameId) {
    this.nameId = nameId;
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
  public boolean equals(Object o) {
    if (!(o instanceof NameUsageSuggestion)) return false;
    NameUsageSuggestion that = (NameUsageSuggestion) o;
    return Float.compare(score, that.score) == 0 && Objects.equals(match, that.match) && Objects.equals(context, that.context) && Objects.equals(usageId, that.usageId) && Objects.equals(nameId, that.nameId) && Objects.equals(acceptedUsageId, that.acceptedUsageId) && rank == that.rank && status == that.status && nomCode == that.nomCode && group == that.group;
  }

  @Override
  public int hashCode() {
    return Objects.hash(match, context, usageId, nameId, acceptedUsageId, rank, status, nomCode, score, group);
  }
}
