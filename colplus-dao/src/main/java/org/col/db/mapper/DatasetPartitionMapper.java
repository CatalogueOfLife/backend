package org.col.db.mapper;

import java.util.List;

import com.google.common.collect.Lists;
import org.apache.ibatis.annotations.Param;

public interface DatasetPartitionMapper {
  // order is important !!!
  List<String> TABLES = Lists.newArrayList(
      "verbatim",
      "reference",
      "name",
      "name_rel",
      "name_usage",
      "description",
      "distribution",
      "media",
      "vernacular_name"
  );
  
  /**
   * Creates a new dataset partition for all data tables if not already existing.
   * The tables are not attached to the main partitioned table yet, see attach().
   * Indices are not created yet which should happen after the data is inserted.
   */
  default void create(int key) {
    TABLES.forEach(t -> createTable(t, key));
  }
  
  void createTable(@Param("table") String table, @Param("key") int key);

  /**
   * Deletes a dataset partition from all data tables if existing, but leaves the dataset itself untouched
   *
   * @param key
   */
  default void delete(int key) {
    Lists.reverse(TABLES).forEach(t -> deleteTable(t, key));
  }
 
  void deleteTable(@Param("table") String table, @Param("key") int key);
  
  /**
   * Creates indices on a all partition tables for a given datasetKey.
   *
   * @param key
   */
  void buildIndices(@Param("key") int key);
  
  /**
   * Attaches all dataset specific partition tables to their main table
   * so they become visible.
   *
   * Warning! This requires an AccessExclusiveLock on the main tables
   * which often leads to deadlocks, see https://github.com/Sp2000/colplus-backend/issues/387
   *
   * Best to manually aqcuire and afterwards release a lock first, attaching doesn't take long.
   *
   * @param key
   */
  default void attach(int key) {
    TABLES.forEach(t -> attachTable(t, key));
  }
  
  void attachTable(@Param("table") String table, @Param("key") int key);
}
