package life.catalogue.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Mapper with default methods to manage the lifetime of dataset based partitions
 * and all their need sequences & indices.
 *
 * We deal with dataset partitioning depending on the immutable origin property of the dataset:
 *
 * EXTERNAL: HASH based partitioning on the datasetKey
 * PROJECT: a dedicated partition which provides fast read/write speeds
 * RELEASE: a dedicated partition which provides fast read/write speeds and allows for quick deletions of entire releases when they get old
 *
 * This assumes that the total number of managed and released datasets is well below 1000.
 * If we ever exceed these numbers we should relocate partitions.
 *
 * For COL itself it is important to have speedy releases and quick responses when managing the project,
 * so these should always live on their own partition.
 */
public interface DatasetPartitionMapper {
  Logger LOG = LoggerFactory.getLogger(DatasetPartitionMapper.class);

  List<String> IDMAP_TABLES = Lists.newArrayList(IdMapMapper.NAME_TBL, IdMapMapper.USAGE_TBL);

  // order is important !!!
  List<String> PARTITIONED_TABLES = Lists.newArrayList(
      "verbatim",
      "reference",
      "name",
      "name_rel",
      "name_match",
      "type_material",
      "name_usage",
      "verbatim_source",
      "verbatim_source_secondary",
      "taxon_concept_rel",
      "species_interaction",
      "distribution",
      "media",
      "estimate",
      "taxon_property",
      "taxon_metrics",
      "treatment",
      "vernacular_name"
  );

  List<String> SERIAL_TABLES = Lists.newArrayList(
    "sector",
    "decision",
      "verbatim",
      "name_rel",
      "taxon_concept_rel",
      "species_interaction",
      "distribution",
      "media",
      "estimate",
      "taxon_property",
      "vernacular_name"
  );

  /**
   * Creates the given number of partitions for all partitioned tables.
   * @param number of partitions per table
   */
  default void createPartitions(int number) {
    PARTITIONED_TABLES.forEach(t -> createPartitions(t, number));
    // create triggers
    for (int i=0; i<number; i++) {
      String suffix = "mod"+i;
      attachTriggers(suffix);
    }
  }

  /**
   * Creates the given number of partitions for all partitioned tables.
   * @param number of partitions per table
   * @param table name
   */
  default void createPartitions(String table, int number) {
    for (int i=0; i<number; i++) {
      createPartition(table, number, i);
    }
  }

  void createPartition(@Param("table") String table, @Param("modulus") int modulus, @Param("remainder") int remainder);

  void createIdMapTable(@Param("table") String table, @Param("key") int key);

  /**
   * Creates a new standalone id sequence named after the table and dataset key
   * @param table
   * @param key
   */
  void createIdSequence(@Param("table") String table, @Param("key") int key);

  void updateIdSequence(@Param("table") String table, @Param("key") int key);

  void deleteIdSequence(@Param("table") String table, @Param("key") int key);

  default void createSequences(@Param("key") int key) {
    SERIAL_TABLES.forEach(t -> createIdSequence(t, key));
  }

  /**
   * Updates the sequences for a given datasetKey to the current max of existing keys.
   * @param key datasetKey
   */
  default void updateSequences(int key) {
    SERIAL_TABLES.forEach(t -> updateIdSequence(t, key));
  }

  default void deleteSequences(@Param("key") int key) {
    SERIAL_TABLES.forEach(t -> deleteIdSequence(t, key));
  }

  /**
   * Deletes all data from a table for the given datasetKey.
   * @param key datasetKey
   */
  void deleteData(@Param("table") String table, @Param("key") int key);

  void dropTable(@Param("table") String table, @Param("key") int key);

  void deleteUsageCounter(@Param("key") int key);

  /**
   * Updates the name usage counter record with the current count.
   * Make sure to call this AFTER the partition table is attached
   * @param key datasetkey
   */
  int updateUsageCounter(@Param("key") int key);

  /**
   * Attaches all required triggers for a given partition suffix.
   * Currently these are 2 triggers on the name usage partition to track counts.
   * Make sure to call this AFTER the partition table is attached.
   * @param suffix partition suffix, e.g. mod1
   */
  void attachTriggers(@Param("suffix") String suffix);

  /**
   * Return the list of columns for a given table igoring "doc" columns
   */
  List<String> columns(@Param("t") String table);

}
