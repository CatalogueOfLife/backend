package life.catalogue.api.event;

public class UserPermissionChanged {
  public final String username;

  public UserPermissionChanged(String username) {
    this.username = username;
  }

}
