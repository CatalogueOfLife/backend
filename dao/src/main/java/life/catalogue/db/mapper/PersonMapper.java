package life.catalogue.db.mapper;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * The person registry tables. Rows are plain classes as MyBatis maps no records; they are written by
 * {@link life.catalogue.matching.person.PersonTables} with COPY, never by this mapper.
 */
public interface PersonMapper {

  class PersonRow {
    /** the id the person was asked for by, set by the lookups by any id only */
    public String anyId;
    public String id;
    public String wikidata;
    public String ipni;
    public String zoobank;
    public List<String> formerIds;
    public String family;
    public String given;
    public String suffix;
    public Integer born;
    public Integer died;
    public Integer activeFrom;
    public Integer activeTo;
    public Set<TaxGroup> groups;
    public PersonSource source;
    public LocalDate retired;
    public String successor;
    public LocalDateTime created;
    public LocalDateTime modified;

    public Person toPerson() {
      return new Person(id, wikidata, ipni, zoobank, formerIds == null ? List.of() : List.copyOf(formerIds), family, given,
        suffix, born, died, activeFrom, activeTo, groups == null ? Set.of() : Set.copyOf(groups), source, retired, successor);
    }
  }

  class NameRow {
    public String personId;
    public String form;
    public PersonNameKind kind;
    public PersonFormCode code;
    public PersonSource source;

    public PersonName toName() {
      return new PersonName(personId, form, kind, code, source);
    }
  }

  class RelationRow {
    public String personId;
    public PersonRelationType relation;
    public String otherId;
    public PersonSource source;

    public PersonRelation toRelation() {
      return new PersonRelation(personId, relation, otherId, source);
    }
  }

  class KeyId {
    public String key;
    public String personId;
  }

  /**
   * @return every person
   */
  List<PersonRow> list();

  /**
   * @return every name form but the derived ones
   */
  List<NameRow> listNames();

  List<RelationRow> listRelations();
}
