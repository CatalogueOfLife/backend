package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;

public interface DatasetPartitionMapper {

	/**
	 * Creates a new dataset partition for all data tables if not already existing
	 * Indices are not created yet which should happen after the data is inserted by calling attach
	 *
	 * Warning! We create many tables and attach them to the partitioned table in one transaction.
	 * This can easily lead to table deadlocks, see https://github.com/Sp2000/colplus-backend/issues/127
	 * Only use this mapper through the corresponding DAO which guarantees single threaded access!
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

	/**
	 * Truncates all data from a dataset cascading to all entities incl names, taxa
	 * and references.
	 *
	 * @param key
	 */
	void truncateDatasetData(@Param("key") int key);
}
