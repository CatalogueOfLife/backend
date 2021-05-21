package life.catalogue.api.event;

import life.catalogue.api.model.User;

public class UserChanged extends EntityChanged<Integer, User> {

  private UserChanged(EventType type, Integer key, User obj, User old) {
    super(type, key, obj, old, User.class);
  }

  public static UserChanged deleted(User u){
    return new UserChanged(EventType.DELETE, u.getKey(), null, u);
  }

  public static UserChanged changed(User u){
    return new UserChanged(EventType.UPDATE, u.getKey(), u, null);
  }

  public static UserChanged created(User u){
    return new UserChanged(EventType.CREATE, u.getKey(), u, null);
  }
}
