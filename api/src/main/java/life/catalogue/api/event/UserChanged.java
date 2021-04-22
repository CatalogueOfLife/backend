package life.catalogue.api.event;

import life.catalogue.api.model.User;

public class UserChanged extends EntityChanged<Integer, User> {

  private UserChanged(Integer key, boolean created, User obj, User old) {
    super(key, obj, old, created, User.class);
  }

  public static UserChanged delete(User u){
    return new UserChanged(u.getKey(), false, null, u);
  }

  public static UserChanged change(User u){
    return new UserChanged(u.getKey(), false, u, null);
  }

  public static UserChanged created(User u){
    return new UserChanged(u.getKey(), true, u, null);
  }
}
