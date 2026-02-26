package life.catalogue.api.model;

import life.catalogue.api.vocab.NameField;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static life.catalogue.api.vocab.NameField.*;
import static life.catalogue.api.vocab.NameField.ACCORDING_TO;
import static life.catalogue.api.vocab.NameField.BASIONYM_AUTHORS;
import static life.catalogue.api.vocab.NameField.BASIONYM_EX_AUTHORS;
import static life.catalogue.api.vocab.NameField.BASIONYM_YEAR;
import static life.catalogue.api.vocab.NameField.CANDIDATUS;
import static life.catalogue.api.vocab.NameField.CODE;
import static life.catalogue.api.vocab.NameField.COMBINATION_AUTHORS;
import static life.catalogue.api.vocab.NameField.COMBINATION_EX_AUTHORS;
import static life.catalogue.api.vocab.NameField.COMBINATION_YEAR;
import static life.catalogue.api.vocab.NameField.CULTIVAR_EPITHET;
import static life.catalogue.api.vocab.NameField.INFRASPECIFIC_EPITHET;
import static life.catalogue.api.vocab.NameField.NAME_PHRASE;
import static life.catalogue.api.vocab.NameField.NOMENCLATURAL_NOTE;
import static life.catalogue.api.vocab.NameField.NOM_STATUS;
import static life.catalogue.api.vocab.NameField.NOTHO;
import static life.catalogue.api.vocab.NameField.PUBLISHED_IN;
import static life.catalogue.api.vocab.NameField.PUBLISHED_IN_PAGE;
import static life.catalogue.api.vocab.NameField.REMARKS;
import static life.catalogue.api.vocab.NameField.SANCTIONING_AUTHOR;
import static life.catalogue.api.vocab.NameField.SPECIFIC_EPITHET;
import static life.catalogue.api.vocab.NameField.UNPARSED;
import static life.catalogue.common.collection.CollectionUtils.notEmpty;

/**
 *
 */
public interface NameUsage extends DSID<String>, VerbatimEntity, VerbatimSourceEntity, SectorScoped, NameUsageCore, Remarkable {

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

  default NameUsageBase asUsageBase() {
    return this instanceof NameUsageBase ? (NameUsageBase) this : null;
  }
  default Taxon asTaxon() {
    return this instanceof Taxon ? (Taxon) this : null;
  }
  default Synonym asSynonym() {
    return this instanceof Synonym ? (Synonym) this : null;
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

  default SimpleNameClassified<SimpleNameCached> toSimpleNameClassified(Integer canonicalNidx) {
    return new SimpleNameClassified<>(asUsageBase(), canonicalNidx);
  }

  default SimpleNameWithNidx toSimpleNameWithNidx(Function<Integer, Integer> nidx2canonical) {
    SimpleNameWithNidx sn = new SimpleNameWithNidx(getName(), nidx2canonical.apply(getName().getNamesIndexId()));
    sn.setStatus(getStatus());
    sn.setCode(getName().getCode());
    sn.setParent(getParentId());
    return sn;
  }

  @JsonIgnore
  default Set<NameField> nonNullNameFields() {
    Set<NameField> fields = EnumSet.noneOf(NameField.class);
    Name name = getName();
    addIfSet(fields, UNINOMIAL, name.getUninomial());
    addIfSet(fields, GENUS, name.getGenus());
    addIfSet(fields, INFRAGENERIC_EPITHET, name.getInfragenericEpithet());
    addIfSet(fields, SPECIFIC_EPITHET, name.getSpecificEpithet());
    addIfSet(fields, INFRASPECIFIC_EPITHET, name.getInfraspecificEpithet());
    addIfSet(fields, CULTIVAR_EPITHET, name.getCultivarEpithet());
    if (name.isCandidatus()) {
      fields.add(CANDIDATUS);
    }
    addIfSet(fields, NOTHO, name.getNotho());
    if (name.getBasionymAuthorship() != null) {
      addIfSet(fields, BASIONYM_AUTHORS, name.getBasionymAuthorship().getAuthors());
      addIfSet(fields, BASIONYM_EX_AUTHORS, name.getBasionymAuthorship().getExAuthors());
      addIfSet(fields, BASIONYM_YEAR, name.getBasionymAuthorship().getYear());
    }
    if (name.getCombinationAuthorship() != null) {
      addIfSet(fields, COMBINATION_AUTHORS, name.getCombinationAuthorship().getAuthors());
      addIfSet(fields, COMBINATION_EX_AUTHORS, name.getCombinationAuthorship().getExAuthors());
      addIfSet(fields, COMBINATION_YEAR, name.getCombinationAuthorship().getYear());
    }
    addIfSet(fields, SANCTIONING_AUTHOR, name.getSanctioningAuthor());
    addIfSet(fields, CODE, name.getCode());
    addIfSet(fields, NOM_STATUS, name.getNomStatus());
    addIfSet(fields, PUBLISHED_IN, name.getPublishedInId());
    addIfSet(fields, PUBLISHED_IN_PAGE, name.getPublishedInPage());
    addIfSet(fields, NOMENCLATURAL_NOTE, name.getNomenclaturalNote());
    addIfSet(fields, UNPARSED, name.getUnparsed());
    addIfSet(fields, REMARKS, name.getRemarks());
    // NameUsage fields
    addIfSet(fields, REMARKS, getRemarks());
    addIfSet(fields, NAME_PHRASE, getNamePhrase());
    addIfSet(fields, ACCORDING_TO, getAccordingToId());
    return fields;
  }
  private static void addIfSet(Set<NameField> fields, NameField nf, Collection<?> val) {
    if (notEmpty(val)) {
      fields.add(nf);
    }
  }

  private static void addIfSet(Set<NameField> fields, NameField nf, Object val) {
    if (val != null) {
      fields.add(nf);
    }
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
