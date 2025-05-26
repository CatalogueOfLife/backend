package life.catalogue.api.event;

import life.catalogue.api.model.DataEntity;

import com.google.common.base.Preconditions;

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

  /**
   * Creates a change event for newly created instances with just the new (obj) instance.
   */
  public static <K, T extends DataEntity<K>>  EntityChanged<K,T> created(T obj, int user){
    return new EntityChanged<>(CREATE, obj.getKey(), obj, null, user, (Class<T>) obj.getClass());
  }

  /**
   * Creates a change event for updates with both the new (obj) and old property.
   */
  public static <K, T extends DataEntity<K>>  EntityChanged<K,T> change(T obj, T old, int user){
    return new EntityChanged<>(UPDATE, obj.getKey(), obj, old, user, (Class<T>) obj.getClass());
  }

  /**
   * Creates a change event for deletions with just the old property, i.e. how the instance was before the deletion.
   */
  public static <K, T> EntityChanged<K, T> delete(K key, T old, int user, Class<T> objClass){
    return new EntityChanged<>(DELETE, key, null, old, user, objClass);
  }

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
