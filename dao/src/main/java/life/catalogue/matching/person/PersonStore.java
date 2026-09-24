package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.api.vocab.PersonFormCode;

import org.gbif.nameparser.api.NomCode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The person registry as author matching reads it. Persons are found by any id they answer to - their own, a former id
 * or a prefixed authority id - or by the {@link PersonKeys#key(String)} of a form, derived ones included. A form applies
 * to a name by its code, see {@link PersonFormCode#appliesTo(NomCode)}. A key may name several persons: a bare surname
 * proposes candidates only.
 */
public interface PersonStore {

  /**
   * @param anyId an id, a former id or a prefixed authority id
   * @return the person, null for none
   */
  @Nullable
  Person get(String anyId);

  /**
   * @return the persons with a form under the key whose code applies, empty for none
   */
  Set<Person> byKey(String key, @Nullable NomCode code);

  /**
   * @return the persons of every key that has any, as {@link #byKey}
   */
  Map<String, Set<Person>> byKeys(Collection<String> keys, @Nullable NomCode code);

  /**
   * @return parents, children and siblings
   */
  Set<Person> relatives(Person p);

  /**
   * @return the keys of every form of the person whose code applies, derived ones included
   */
  Set<String> keys(Person p, @Nullable NomCode code);

  /**
   * @return the person with its forms, derived ones excluded, and its relations; null for an id nobody answers to
   */
  @Nullable
  PersonInfo info(String anyId);

  /**
   * @param citation an author as cited, or already normalized, which folds to the same key
   * @return the persons the citation may name under the code of the name
   */
  default Set<Person> candidates(String citation, @Nullable NomCode code) {
    String key = PersonKeys.key(citation);
    return key == null ? Set.of() : byKey(key, code);
  }
}
