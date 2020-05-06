package life.catalogue.api.event;

import life.catalogue.api.model.User;

public class EditorChanged extends EntityChanged<Integer, User> {
  public final String username;

  private EditorChanged(String username, Integer key, User obj) {
    super(key, obj, User.class);
    this.username = username;
  }

  public static EditorChanged delete(String username, int key){
    return new EditorChanged(username, key, null);
  }

  public static EditorChanged change(User u){
    return new EditorChanged(u.getUsername(), u.getKey(), u);
  }
}
