package life.catalogue.api.event;

import life.catalogue.api.model.User;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A user was created, updated or deleted, so its cached permissions are outdated.
 */
public class UserChanged implements Event {
  public EventType type;
  public int key;
  public String username;
  /** the user who made the change */
  public int user;

  public UserChanged() {
  }

  private UserChanged(EventType type, User u, int user) {
    this.type = type;
    this.key = Objects.requireNonNull(u.getKey(), "user key required");
    this.username = u.getUsername();
    this.user = user;
  }

  public static UserChanged deleted(User u, int user){
    return new UserChanged(EventType.DELETE, u, user);
  }

  public static UserChanged changed(User u, int user){
    return new UserChanged(EventType.UPDATE, u, user);
  }

  public static UserChanged created(User u, int user){
    return new UserChanged(EventType.CREATE, u, user);
  }

  @JsonIgnore
  public boolean isDeletion(){
    return type == EventType.DELETE;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof UserChanged that)) return false;
    return key == that.key && user == that.user && type == that.type && Objects.equals(username, that.username);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, key, username, user);
  }

  @Override
  public String toString() {
    return "UserChanged{" + type + " " + key + " " + username + '}';
  }
}
