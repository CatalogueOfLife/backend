package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Dataset;
import org.col.api.Page;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface DatasetMapper {

	int count(@Nullable @Param("q") String q);

	List<Dataset> list(@Param("page") Page page);

	List<Dataset> search(@Nullable @Param("q") String q, @Param("page") Page page);

	/**
	 * Page through all datasets, listing datasets which have not been imported
	 * before.
	 */
	List<Dataset> listEmpty(@Param("page") Page page);

	Dataset get(@Param("key") int key);

	Dataset getByGBIF(@Param("key") UUID key);

	void create(Dataset dataset);

	int update(Dataset dataset);

	/**
	 * Marks a dataset as deleted
	 * 
	 * @param key
	 */
	int delete(@Param("key") int key);

	/**
	 * Truncates all data from a dataset cascading to all entities incl names, taxa
	 * and references.
	 * 
	 * @param key
	 */
	void truncateDatasetData(@Param("key") int key);
}
