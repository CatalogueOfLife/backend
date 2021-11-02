package life.catalogue.api.model;

import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public interface NameUsage extends DSID<String>, VerbatimEntity, RankedID {

  String getLabel();

  String getLabelHtml();

  Name getName();
  
  void setName(Name name);

  TaxonomicStatus getStatus();
  
  void setStatus(TaxonomicStatus status);

  String getNamePhrase();

  void setNamePhrase(String namePhrase);

  Origin getOrigin();
  
  void setOrigin(Origin origin);
  
  String getAccordingToId();
  
  void setAccordingToId(String according);

  @JsonIgnore
  default Rank getRank() {
    return getName() == null ? null : getName().getRank();
  }

  @JsonIgnore
  default boolean isSynonym() {
    return getStatus() != null && getStatus().isSynonym();
  }

  @JsonIgnore
  default boolean isTaxon() {
    return getStatus() != null && getStatus().isTaxon();
  }

  @JsonIgnore
  default boolean isBareName() {
    return getStatus() == null || getStatus().isBareName();
  }

  String getRemarks();
  
  void setRemarks(String remarks);
}
