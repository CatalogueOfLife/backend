package life.catalogue.matching.person;

/**
 * One way a person is cited or named.
 *
 * @param person any id of the person, see {@link Person#allIds()}
 */
public record PersonName(String person, String form, NameKind kind, FormCode code, Provenance source) {
}
