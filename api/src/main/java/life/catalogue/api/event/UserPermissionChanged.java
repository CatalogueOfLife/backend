package life.catalogue.api.event;

import java.util.Objects;

public class UserPermissionChanged implements Event {
  public String username;

  public UserPermissionChanged() {
  }

  public UserPermissionChanged(String username) {
    this.username = username;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof UserPermissionChanged)) return false;
    UserPermissionChanged that = (UserPermissionChanged) o;
    return Objects.equals(username, that.username);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(username);
  }
}
