package life.catalogue.api.event;

import java.util.Objects;

/**
 * The person registry was rewritten, by a harvest or an import: every cache of it is stale.
 */
public class PersonsChanged implements Event {
  public int user;

  public PersonsChanged() {
  }

  public PersonsChanged(int user) {
    this.user = user;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PersonsChanged that && user == that.user;
  }

  @Override
  public int hashCode() {
    return Objects.hash(user);
  }

  @Override
  public String toString() {
    return "PersonsChanged{user=" + user + '}';
  }
}
