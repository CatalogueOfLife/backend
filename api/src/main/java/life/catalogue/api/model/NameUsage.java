package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

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
    return getStatus() != null && !getStatus().isSynonym();
  }

  @JsonIgnore
  default boolean isBareName() {
    return getStatus() == null;
  }

  String getRemarks();
  
  void setRemarks(String remarks);
}
