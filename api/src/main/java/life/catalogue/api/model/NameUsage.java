package life.catalogue.api.model;

import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public interface NameUsage extends DSID<String>, VerbatimEntity, SectorScoped, NameUsageCore, Remarkable {

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

  String getAccordingTo();

  void setAccordingTo(String accordingTo);

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

  default Taxon asTaxon() {
    return this instanceof Taxon ? (Taxon) this : null;
  }

  @JsonIgnore
  default boolean isBareName() {
    return getStatus() == null || getStatus().isBareName();
  }

  default SimpleNameLink toSimpleNameLink() {
    SimpleNameLink sn = SimpleNameLink.of(getId(), getName().getScientificName(), getName().getAuthorship(), getName().getRank());
    sn.setStatus(getStatus());
    sn.setCode(getName().getCode());
    sn.setParent(getParentId());
    return sn;
  }

  default SimpleNameWithNidx toSimpleNameWithNidx(Function<Integer, Integer> nidx2canonical) {
    SimpleNameWithNidx sn = new SimpleNameWithNidx(getName(), nidx2canonical.apply(getName().getNamesIndexId()));
    sn.setStatus(getStatus());
    sn.setCode(getName().getCode());
    sn.setParent(getParentId());
    return sn;
  }

  /**
   * factory to create a name usage instance based on the provided status and name.
    */
  static NameUsage create(TaxonomicStatus status, Name n) {
    status = status == null ? TaxonomicStatus.ACCEPTED : status;
    NameUsage nu;
    if (status == TaxonomicStatus.BARE_NAME) {
      nu = new BareName(n);
    } else {
      if (status.isSynonym()) {
        nu = new Synonym(n);
      } else {
        nu = new Taxon(n);
      }
      nu.setStatus(status);
    }
    return nu;
  }
}
