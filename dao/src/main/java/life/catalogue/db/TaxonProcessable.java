package life.catalogue.db;

import life.catalogue.api.model.DSID;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Minimal mapper to deal with entities of type V that are related to a single taxon.
 * @param <T> entity type
 */
public interface TaxonProcessable<T> {

  List<T> listByTaxon(@Param("key") DSID<String> key);

}