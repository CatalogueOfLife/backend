package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Reference;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import org.apache.ibatis.annotations.Param;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 *
 */
public interface ReferenceMapper extends CRUD<DSID<String>, Reference>, ProcessableDataset<Reference>, DatasetPageable<Reference> {
	
	/**
   * Selects a number of distinct references from a single dataset by their keys
   *
   * @param ids must contain at least one value, not allowed to be empty !!!
   */
  List<Reference> listByIds(@Param("datasetKey") int datasetKey, @Param("ids") Set<String> ids);
	
	/**
	 * @return all bibliographic reference from a dataset by its full citation, optionally limited to a single sector
	 */
	List<Reference> find(@Param("datasetKey") int datasetKey, @Param("sectorKey") Integer sectorKey, @Param("citation") String citation);
	
	List<Reference> search(@Param("datasetKey") int datasetKey, @Param("req") ReferenceSearchRequest request, @Param("page") Page page);
	
	int searchCount(@Param("datasetKey") int datasetKey, @Param("req") ReferenceSearchRequest request);

	int deleteBySector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);

	/**
	 * Deletes all references that have no foreign key pointing to them from any of the other tables.
	 * @param datasetKey the datasetKey to restrict the deletion to
	 * @param before optional timestamp to restrict deletions to orphans before the given time
	 * @return number of deleted references
	 */
	int deleteOrphans(@Param("datasetKey") int datasetKey, @Param("before") @Nullable LocalDateTime before);

}
