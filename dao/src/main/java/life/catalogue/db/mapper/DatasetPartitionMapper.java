package life.catalogue.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Mapper with default methods to manage the lifetime of dataset partitions
 * and all the per dataset sequences and tables that go with them.
 *
 * All tables listed in {@link #PARTITIONED_TABLES} use plain HASH partitioning on dataset_key,
 * with partitions named {table}_mod{remainder}, e.g. name_usage_mod3.
 * The origin of a dataset does not matter: EXTERNAL, PROJECT and RELEASE datasets all share the same hashed partitions.
 * There are no dedicated per dataset partitions and no default partition.
 *
 * The number of partitions is fixed when the database is initialised, see the init command and its --num argument.
 * It can only be changed afterwards by the repartition command, which rebalances all data into a new number of partitions.
 * Beware that repartitioning is currently broken on PG 17.1 and above,
 * see https://github.com/CatalogueOfLife/backend/issues/1424
 *
 * Because partitions are hashed, deleting a dataset cannot drop a partition.
 * It is a regular DELETE by dataset_key across all partitioned tables, see DatasetDao.deleteData
 * which iterates PARTITIONED_TABLES in reverse order to satisfy foreign keys.
 *
 * What does still exist per dataset are standalone, non partitioned objects:
 * the id sequences for all {@link #SERIAL_TABLES} and the temporary id mapping tables for {@link #IDMAP_TABLES},
 * both named after the table and the dataset key, e.g. distribution_3_id_seq or idmap_name_usage_3.
 * {@link #dropTable(String, int)} drops such a per dataset table, never a partition.
 *
 * Keep the number of partitions moderate. Queries restricted to a single dataset get pruned down to one partition,
 * but cross dataset queries without a dataset_key filter must open every partition and Postgres locks
 * each partition together with all of its indexes. For name_usage, name and name_match that is roughly
 * 23 locked relations per partition, so at 24 partitions a single such query holds close to 600 relation locks
 * and puts real pressure on max_locks_per_transaction.
 */
public interface DatasetPartitionMapper {
  Logger LOG = LoggerFactory.getLogger(DatasetPartitionMapper.class);

  List<String> IDMAP_TABLES = Lists.newArrayList(IdMapMapper.NAME_TBL, IdMapMapper.USAGE_TBL);

  // order is important !!! foreign keys require this creation order, deletions must iterate it reversed
  List<String> PARTITIONED_TABLES = Lists.newArrayList(
      "verbatim",
      "verbatim_source",
      "verbatim_source_secondary",
      "reference",
      "name",
      "name_rel",
      "name_match",
      "type_material",
      "name_usage",
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
      attachStatistics(suffix);
    }
  }

  /**
   * Creates the given number of hashed partitions for a single partitioned table.
   * @param number of partitions to create for the table
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
   * Similar to attachTriggers but adds special statistics for the analyzer to the name usage partition only.
   * See https://github.com/CatalogueOfLife/backend/issues/1503
   * @param suffix
   */
  void attachStatistics(@Param("suffix") String suffix);

  /**
   * Return the list of columns for a given table igoring "doc" columns
   */
  List<String> columns(@Param("t") String table);

}
