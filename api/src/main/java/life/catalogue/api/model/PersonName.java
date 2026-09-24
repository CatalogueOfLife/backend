package life.catalogue.api.model;

import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;

/**
 * One way a person is cited or named.
 *
 * @param person any id of the person, see {@link Person#allIds()}
 */
public record PersonName(String person, String form, PersonNameKind kind, PersonFormCode code, PersonSource source) {
}
