package life.catalogue.api.model;

import org.apache.commons.lang3.StringUtils;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.util.NameFormatter;

/**
 *
 */
public interface NameUsage extends DSID<String>, VerbatimEntity {

  default String getLabel() {
    return completeName(false);
  }

  default String getLabelHtml() {
    return completeName(true);
  }

  default ParsedName toParsedName() {
    return Name.toParsedName(this.getName());
  }

  private String completeName(boolean html) {
    return getName().isParsed() ?
            NameFormatter.buildName(toParsedName(), true, true, true, true, true, true, false, true, true, true, true, true, true, html)
            : getName().scientificNameAuthorship();
  }

  Name getName();
  
  void setName(Name name);

  TaxonomicStatus getStatus();
  
  void setStatus(TaxonomicStatus status);
  
  Origin getOrigin();
  
  void setOrigin(Origin origin);
  
  String getAccordingTo();
  
  void setAccordingTo(String according);
  
  default boolean isSynonym() {
    return getStatus() != null && getStatus().isSynonym();
  }
  
  default boolean isTaxon() {
    return getStatus() != null && !getStatus().isSynonym();
  }
  
  default boolean isBareName() {
    return getStatus() == null;
  }
  
  default void addAccordingTo(String accordingTo) {
    if (!StringUtils.isBlank(accordingTo)) {
      setAccordingTo( getAccordingTo() == null ? accordingTo.trim() : getAccordingTo() + " " + accordingTo.trim());
    }
  }
  
  String getRemarks();
  
  void setRemarks(String remarks);
}
