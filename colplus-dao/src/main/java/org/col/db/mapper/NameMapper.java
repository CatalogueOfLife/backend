package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Name;
import org.col.api.model.Page;

/**
 *
 */
public interface NameMapper {

	int count(@Param("datasetKey") int datasetKey);

	List<Name> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

	/**
	 * Iterates over all names of a given dataset and processes them with the supplied handler.
	 * This allows a single query to efficiently stream all its values without keeping them in memory.
	 *
	 * @param handler to process each name with
	 */
	void processDataset(@Param("datasetKey") int datasetKey, ResultHandler<Name> handler);

	Name get(@Param("datasetKey") int datasetKey, @Param("id") String id);

	Name getByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);

  /**
   * Creates a new name.
   * If the homotypic group key is not yet set the newly created name key will be
   * used to point to the name itself
   * @param name
   */
  void create(Name name);

	/**
	 * Lists all homotypic names based on the same homotypic name key
   *
	 * @param nameId name id of the homotypic group
	 */
  List<Name> homotypicGroup(@Param("datasetKey") int datasetKey, @Param("id") String nameId);

  /**
   * Returns the list of names published in the same reference.
   */
  List<Name> listByReference(@Param("datasetKey") int datasetKey, @Param("refId") String publishedInId);

	/**
	 * Lists all names with the same index name key
	 * across all datasets.
	 *
	 * @param nameId from the names index
	 */
	List<Name> indexGroup(@Param("id") String nameId);
}
