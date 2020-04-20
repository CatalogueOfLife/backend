package life.catalogue.api.event;

import life.catalogue.api.model.User;

public class UserChanged extends EntityChanged<Integer, User> {
  public final String username;

  private UserChanged(String username, Integer key, User obj) {
    super(key, obj, User.class);
    this.username = username;
  }

  public static UserChanged delete(String username, int key){
    return new UserChanged(username, key, null);
  }

  public static UserChanged change(User u){
    return new UserChanged(u.getUsername(), u.getKey(), u);
  }
}
