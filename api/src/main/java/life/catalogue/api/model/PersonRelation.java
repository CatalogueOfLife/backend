package life.catalogue.api.model;

import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;

/**
 * @param person any id of the person, see {@link Person#allIds()}
 * @param other  any id of the related person. For {@link PersonRelationType#PARENT} it is the parent of person
 */
public record PersonRelation(String person, PersonRelationType relation, String other, PersonSource source) {
}
