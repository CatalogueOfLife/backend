package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;

public interface DatasetPartitionMapper {

	/**
	 * Creates a new dataset partition for all data tables if not already existing.
	 * The tables are not attached to the main partitioned table yet, see attach().
	 * Indices are not created yet which should happen after the data is inserted.
	 */
	void create(@Param("key") int key);

	/**
	 * Deletes a dataset partition from all data tables if existing, but leaves the dataset itself untouched
	 * @param key
	 */
	void delete(@Param("key") int key);

	/**
	 * Creates indices on a all partition tables for a given datasetKey.
	 * @param key
	 */
	void buildIndices(@Param("key") int key);

	/**
	 * Attaches all dataset specific partition tables to their main table
	 * so they become visible.
	 * @param key
	 */
	void attach(@Param("key") int key);
}
