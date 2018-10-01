package org.col.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.ColSource;

public interface ColSourceMapper extends CRUDMapper<ColSource> {

	List<ColSource> list(@Param("key") @Nullable Integer datasetKey);

	/**
	 * Returns the source without any defaults from dataset for null properties.
	 * Suitable for editing the entity.
	 */
	ColSource getEditable(@Param("key") int key);

}
