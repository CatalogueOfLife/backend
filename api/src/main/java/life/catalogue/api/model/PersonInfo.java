package life.catalogue.api.model;

import java.util.List;

/**
 * A person with the name forms sources and curators gave it and the relations it has to others.
 */
public record PersonInfo(Person person, List<PersonName> names, List<PersonRelation> relations) {
}
