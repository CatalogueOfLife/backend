package org.col.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.ColSource;

public interface ColSourceMapper {

	List<ColSource> list(@Param("key") @Nullable Integer datasetKey);

	/**
	 * @param incDefaults if true uses defaults from dataset for null properties
	 */
	ColSource get(@Param("key") int key, @Param("full") boolean incDefaults);

	void create(ColSource source);

	int update(ColSource source);

	/**
	 * Marks a source as deleted
	 * 
	 * @param key
	 */
	int delete(@Param("key") int key);

}
