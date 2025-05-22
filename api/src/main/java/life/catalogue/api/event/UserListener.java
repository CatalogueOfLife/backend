package life.catalogue.api.event;

public interface UserListener extends Listener {

  void userChanged(UserChanged event);

  void userPermissionChanged(UserPermissionChanged event);

}
