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
public class EntityChanged<K, T> {
  public final EventType type;
  public final K key;
  public final T obj;
  public final T old;
  public final Class<T> objClass;

  public static <K, T extends DataEntity<K>>  EntityChanged<K,T> created(T obj){
    return new EntityChanged<>(CREATE, obj.getKey(), obj, null, (Class<T>) obj.getClass());
  }

  public static <K, T extends DataEntity<K>>  EntityChanged<K,T> change(T obj, T old){
    return new EntityChanged<>(UPDATE, obj.getKey(), obj, old, (Class<T>) obj.getClass());
  }

  public static <K, T> EntityChanged<K, T> delete(K key, T old, Class<T> objClass){
    return new EntityChanged<>(DELETE, key, null, old, objClass);
  }

  EntityChanged(EventType type, K key, T obj, T old, Class<T> objClass) {
    this.type = type;
    this.key = Preconditions.checkNotNull(key);
    this.obj = obj; // can be null in case of deletions
    this.old = old;
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
}
