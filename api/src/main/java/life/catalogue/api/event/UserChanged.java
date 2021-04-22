package life.catalogue.api.event;

import life.catalogue.api.model.User;

public class UserChanged extends EntityChanged<Integer, User> {
  public final String username;

  private UserChanged(String username, Integer key, boolean created, User obj, User old) {
    super(key, obj, old, created, User.class);
    this.username = username;
  }

  public static UserChanged delete(String username, int key){
    return new UserChanged(username, key, false, null, null);
  }

  public static UserChanged change(User u){
    return new UserChanged(u.getUsername(), u.getKey(), false, u, null);
  }

  public static UserChanged created(User u){
    return new UserChanged(u.getUsername(), u.getKey(), true, u, null);
  }
}
