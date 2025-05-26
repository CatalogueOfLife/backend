package life.catalogue.api.event;

public class UserPermissionChanged implements Event {
  public String username;

  public UserPermissionChanged() {
  }

  public UserPermissionChanged(String username) {
    this.username = username;
  }

}
