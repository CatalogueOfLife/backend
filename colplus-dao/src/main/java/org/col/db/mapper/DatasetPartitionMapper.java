package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;

public interface DatasetPartitionMapper {

	/**
	 * Creates a new dataset partition for all data tables if not already existing
	 * Indices are not created yet which should happen after the data is inserted by calling buildIndices
	 * @param key
	 */
	void create(@Param("key") int key);

	/**
	 * Deletes a dataset partition from all data tables if existing, but leaves the dataset itself untouched
	 * @param key
	 */
	void delete(@Param("key") int key);

	/**
	 * Creates indices on a specific dataset partition for all data tables.
	 * @param key
	 */
	void buildIndices(@Param("key") int key);

	/**
	 * Truncates all data from a dataset cascading to all entities incl names, taxa
	 * and references.
	 *
	 * @param key
	 */
	void truncateDatasetData(@Param("key") int key);
}
