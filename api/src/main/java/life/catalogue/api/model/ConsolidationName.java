package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A small class representing a name usage with an id to be used for name consolidation with effective memory usage.
 */
public class ConsolidationName implements HasID<String> {

  private String id;
  private String name;
  private String authorship;
  private Rank rank;
  private NameType type;
  private TaxonomicStatus status;
  private Integer sectorKey;
  private String consolidatedId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }

  @JsonIgnore
  public boolean hasAuthorship() {
    return !StringUtils.isBlank(authorship);
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public NameType getType() {
    return type;
  }

  public void setType(NameType type) {
    this.type = type;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  @JsonIgnore
  public boolean isSynonym() {
    return status == null || status.isSynonym();
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public Integer getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  public boolean isConsolidated() {
    return consolidatedId != null;
  }

  public String getConsolidatedId() {
    return consolidatedId;
  }

  public void setConsolidatedId(String consolidatedId) {
    this.consolidatedId = consolidatedId;
  }

  public DSID<String> toDSID(int datasetKey){
    return DSID.of(datasetKey, id);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (status != null) {
      sb.append(status)
        .append(" ");
    }
    if (rank != null) {
      sb.append(rank)
        .append(" ");
    }
    if (name != null) {
      sb.append(name);
    }
    if (authorship != null) {
      sb.append(" ")
        .append(authorship);
    }
    if (id != null) {
      sb.append(" [")
        .append(id)
        .append("]");
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ConsolidationName)) return false;

    ConsolidationName that = (ConsolidationName) o;
    return Objects.equals(id, that.id) &&
      Objects.equals(name, that.name) &&
      Objects.equals(authorship, that.authorship) &&
      rank == that.rank &&
      type == that.type &&
      status == that.status &&
      Objects.equals(sectorKey, that.sectorKey) &&
      Objects.equals(consolidatedId, that.consolidatedId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, authorship, rank, type, status, sectorKey, consolidatedId);
  }
}
