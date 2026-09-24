package life.catalogue.api.model;

import java.util.List;

import javax.annotation.Nullable;

/**
 * An author citation matched to the persons of the registry.
 *
 * @param key        the citation folded as it is looked up, null if nothing is left of it to look up
 * @param candidates the one person for RESOLVED, several for AMBIGUOUS, none for UNKNOWN, and for RULED_OUT the persons
 *                   the year or group of the name excluded
 */
public record PersonMatch(String citation, @Nullable String key, Status status, List<Person> candidates) {

  public enum Status {
    /** the citation names one person */
    RESOLVED,
    /** the citation may name several persons */
    AMBIGUOUS,
    /** no person has a form under the key */
    UNKNOWN,
    /** persons have a form under the key, but the year or the group of the name excludes every one of them */
    RULED_OUT
  }
}
