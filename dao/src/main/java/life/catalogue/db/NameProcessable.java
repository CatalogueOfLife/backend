package life.catalogue.db;

import life.catalogue.api.model.DSID;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Minimal mapper to deal with entities of type V that are related to a single name.
 * @param <T> entity type
 */
public interface NameProcessable<T> {

  List<T> listByName(@Param("key") DSID<String> key);

}