package life.catalogue.matching.person;

/**
 * @param person any id of the person, see {@link Person#allIds()}
 * @param other  any id of the related person. For {@link RelationType#PARENT} it is the parent of person
 */
public record PersonRelation(String person, RelationType relation, String other, Provenance source) {
}
