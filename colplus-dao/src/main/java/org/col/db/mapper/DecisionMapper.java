package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.EditorialDecision;

public interface DecisionMapper {

	List<EditorialDecision> listBySector(@Param("key") int sectorKey);

	List<EditorialDecision> listByDataset(@Param("key") int datasetKey);

	EditorialDecision get(@Param("key") int key);

	void create(EditorialDecision decision);

	int update(EditorialDecision decision);

	/**
	 * Removes an editorial decision
	 */
	int delete(@Param("key") int key);

}
