package life.catalogue.api.event;

import life.catalogue.api.model.DataEntity;

import com.google.common.base.Preconditions;

import java.util.Objects;

import static life.catalogue.api.event.EventType.*;
/**
 * A changed entity message for the bus system.
 * Creation or updates result in a changed message with an existing key and obj,
 * deletions result in a change message with a key but a null obj.
 * @param <K> key type
 * @param <T> entity type
 */
public class EntityChanged<K, T> implements Event {
  public EventType type;
  public K key;
  public T obj;
  public T old;
  public int user;
  public Class<T> objClass;

  EntityChanged() {
  }

  EntityChanged(EventType type, K key, T obj, T old, int user, Class<T> objClass) {
    this.type = type;
    this.key = Preconditions.checkNotNull(key);
    this.obj = obj; // can be null in case of deletions
    this.old = old;
    this.user = user;
    this.objClass = Preconditions.checkNotNull(objClass);
  }

  public boolean isDeletion(){
    return type ==  DELETE;
  }

  public boolean isCreated(){
    return type ==  CREATE;
  }

  public boolean isUpdated(){
    return type ==  UPDATE;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof EntityChanged)) return false;
    EntityChanged<?, ?> that = (EntityChanged<?, ?>) o;
    return user == that.user && type == that.type && Objects.equals(key, that.key) && Objects.equals(obj, that.obj) && Objects.equals(old, that.old) && Objects.equals(objClass, that.objClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, key, obj, old, user, objClass);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
      "type=" + type +
      ", key=" + key +
      ", obj=" + obj +
      ", old=" + old +
      ", user=" + user +
      ", objClass=" + objClass +
      '}';
  }
}
