package life.catalogue.api.event;

import life.catalogue.api.model.User;

public class UserChanged extends EntityChanged<Integer, User> {

  private UserChanged(EventType type, Integer key, User obj, User old, int user) {
    super(type, key, obj, old, user, User.class);
  }

  public static UserChanged deleted(User u, int user){
    return new UserChanged(EventType.DELETE, u.getKey(), null, u, user);
  }

  public static UserChanged changed(User u, int user){
    return new UserChanged(EventType.UPDATE, u.getKey(), u, null, user);
  }

  public static UserChanged created(User u, int user){
    return new UserChanged(EventType.CREATE, u.getKey(), u, null, user);
  }
}
