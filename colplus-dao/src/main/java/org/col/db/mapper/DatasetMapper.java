package org.col.db.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;
import org.col.api.Dataset;
import org.col.api.Page;

public interface DatasetMapper {

	int count();

	List<Dataset> list(@Param("page") Page page);

	int countSearchResults(@Param("q") String q);

	List<Dataset> search(@Param("q") String q, @Param("page") Page page);

	Dataset get(@Param("key") int key);

	Dataset getByGBIF(@Param("key") UUID key);

	void create(Dataset dataset);

	void update(Dataset dataset);

	/**
	 * Marks a dataset as deleted
	 * 
	 * @param key
	 */
	void delete(@Param("key") int key);

	/**
	 * Truncates all data from a dataset cascading to all entities incl names, taxa
	 * and references.
	 * 
	 * @param key
	 */
	void truncateDatasetData(@Param("key") int key);
}
