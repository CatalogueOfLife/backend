package org.col.db.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.search.ReferenceSearchRequest;

/**
 *
 */
public interface ReferenceMapper extends DatasetCRUDMapper<Reference> {

	/**
	 * @return all bibliographic reference ids for the given taxon
	 */
	List<String> listByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);

	/**
   * Selects a number of distinct references from a single dataset by their keys
   *
   * @param ids must contain at least one value, not allowed to be empty !!!
   */
  List<Reference> listByIds(@Param("datasetKey") int datasetKey, @Param("ids") Set<String> ids);
  
  /**
   * Links a reference to a taxon
   */
  void linkToTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId, @Param("referenceId") String referenceId);
	
	/**
	 * @return all bibliographic reference from a dataset by its full citation, optionally limited to a single sector
	 */
	List<Reference> find(@Param("datasetKey") int datasetKey, @Param("sectorKey") Integer sectorKey, @Param("citation") String citation);
	
	List<Reference> search(@Param("datasetKey") int datasetKey, @Param("req") ReferenceSearchRequest request, @Param("page") Page page);
	
	int searchCount(@Param("datasetKey") int datasetKey, @Param("req") ReferenceSearchRequest request);

	int deleteBySector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
	
}
