package life.catalogue.api.model;


/**
 *
 * @param <K> primary key type
 */
public interface Entity<K> {

  K getKey();

  void setKey(K key);
}
