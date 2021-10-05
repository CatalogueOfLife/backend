package life.catalogue.db.mapper;

import life.catalogue.api.vocab.DatasetOrigin;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.google.common.collect.Lists;

/**
 * Mapper with default methods to manage the lifetime of dataset based partitions
 * and all their need sequences & indices.
 *
 * We deal with dataset partitioning depending on the immutable origin property of the dataset:
 *
 * EXTERNAL: HASH based partitioning on the datasetKey
 * MANAGED: a dedicated partition which provides fast read/write speeds
 * RELEASED: a dedicated partition which provides fast read/write speeds and allows for quick deletions of entire releases when they get old
 *
 * This assumes that the total number of managed and released datasets is well below 1000.
 * If we ever exceed these numbers we should relocate partitions.
 *
 * For COL itself it is important to have speedy releases and quick responses when managing the project,
 * so these should always live on their own partition.
 */
public interface DatasetPartitionMapper {

  List<String> IDMAP_TABLES = Lists.newArrayList(
    "idmap_name",
    "idmap_name_usage"
  );

  // order is important !!!
  List<String> TABLES = Lists.newArrayList(
      "verbatim",
      "reference",
      "name",
      "name_rel",
      "type_material",
      "name_usage",
      "taxon_concept_rel",
      "species_interaction",
      "distribution",
      "media",
      "treatment",
      "vernacular_name"
  );

  List<String> PROJECT_TABLES = Lists.newArrayList(
    "verbatim_source"
  );
  
  List<String> SERIAL_TABLES = Lists.newArrayList(
      "verbatim",
      "name_rel",
      "taxon_concept_rel",
      "species_interaction",
      "distribution",
      "media",
      "vernacular_name"
  );

  List<String> MANAGED_SERIAL_TABLES = Lists.newArrayList(
    "sector",
    "decision"
  );

  static class FK {
    public final String column;
    public final String table;
    public final boolean defer;
    public final boolean cascade;

    FK(String column, String table) {
      this(column,table,false,true);
    }
    FK(String column, String table, boolean defer, boolean cascade) {
      this.column = column;
      this.table = table;
      this.defer = defer;
      this.cascade = cascade;
    }
  }

  // table -> fk
  // verbatim_key is done automatically for every table and does not need to be included here
  Map<String, List<FK>> FKS = Map.of(
    "name", List.of(
      new FK("published_in_id", "reference")
    ),
    "name_rel", List.of(
      new FK("reference_id", "reference"),
      new FK("name_id", "name"),
      new FK("related_name_id", "name")
    ),
    "type_material", List.of(
      new FK("reference_id", "reference"),
      new FK("name_id", "name")
    ),
    "name_usage", List.of(
      new FK("according_to_id", "reference"),
      new FK("parent_id", "name_usage", true, false),
      new FK("name_id", "name")
    ),
    "taxon_concept_rel", List.of(
      new FK("reference_id", "reference"),
      new FK("taxon_id", "name_usage"),
      new FK("related_taxon_id", "name_usage")
    ),
    "species_interaction", List.of(
      new FK("reference_id", "reference"),
      new FK("taxon_id", "name_usage"),
      new FK("related_taxon_id", "name_usage")
    ),
    "distribution", List.of(
      new FK("reference_id", "reference"),
      new FK("taxon_id", "name_usage")
    ),
    "media", List.of(
      new FK("reference_id", "reference"),
      new FK("taxon_id", "name_usage")
    ),
    "treatment", List.of(
      new FK("id", "name_usage")
    ),
    "vernacular_name", List.of(
      new FK("reference_id", "reference"),
      new FK("taxon_id", "name_usage")
    )
  );

  /**
   * Creates the default partition and its hashed subpartitions
   * @param number of subpartitions
   * @param createDefaultPartition if true also creates the single default parent partition
   */
  default void createDefaultPartitions(int number, boolean createDefaultPartition) {
    if (createDefaultPartition) {
      TABLES.forEach(this::createDefaultPartition);
    }
    for (int i=0; i<number; i++) {
      int remainder = i;
      String suffix = "mod"+remainder;
      TABLES.forEach(t -> createDefaultSubPartition(t, number, remainder));
      // create FKs
      // we always have a verbatim_key on every table
      TABLES.stream()
            .filter(t -> !t.equalsIgnoreCase("verbatim"))
            .forEach(t -> createFk(t, suffix, new FK("dataset_key, verbatim_key", "verbatim")));
      // custom fks
      FKS.forEach( (t,fks) -> fks.forEach(fk -> {
        FK fk2 = new FK("dataset_key, "+fk.column, fk.table, fk.defer, fk.cascade);
        createFk(t, suffix, fk2);
      }));
      attachTriggers(suffix);
    }
  }

  void createDefaultPartition(@Param("table") String table);

  void createDefaultSubPartition(@Param("table") String table, @Param("modulus") int modulus, @Param("remainder") int remainder);

  /**
   * Creates a new dataset partition for all data tables if not already existing.
   * The tables are not attached to the main partitioned table yet, see attach().
   * Indices are not created yet which should happen after the data is inserted.
   * Tables with integer id columns will have their own sequence.
   */
  default void create(int key, DatasetOrigin origin) {
    // create dataset specific sequences regardless which origin
    SERIAL_TABLES.forEach(t -> createIdSequence(t, key));
    // estimates can exist also in non managed datasets, so we need to have an id sequence for them in all datasets
    // but they are not a partitioned table, treat them special
    createIdSequence("estimate", key);
    // things specific to managed and released datasets only
    if (origin.isManagedOrRelease()) {
      // sequences needed to generate keys in managed datasets
      if (origin == DatasetOrigin.MANAGED) {
        createManagedSequences(key);
      }
      TABLES.forEach(t -> {
        createTable(t, key);
        createDatasetKeyCheck(t, key);
      });
      PROJECT_TABLES.forEach(t -> createTable(t, key));
    }
  }
  
  void createTable(@Param("table") String table, @Param("key") int key);

  void createIdMapTable(@Param("table") String table, @Param("key") int key);

  void createDatasetKeyCheck(@Param("table") String table, @Param("key") int key);

  /**
   * Creates a new id sequence and uses it as the default value (serial) for the given tables id column.
   * @param table
   * @param key
   */
  void createSerial(@Param("table") String table, @Param("key") int key);

  /**
   * Creates a new standalone id sequence named after the table and dataset key
   * @param table
   * @param key
   */
  void createIdSequence(@Param("table") String table, @Param("key") int key);

  /**
   * Updates the sequences for a given datasetKey to the current max of existing keys.
   * @param key datasetKey
   */
  default void updateIdSequences(int key) {
    SERIAL_TABLES.forEach(t -> updateIdSequence(t, key));
  }
  
  void updateIdSequence(@Param("table") String table, @Param("key") int key);

  void deleteIdSequence(@Param("table") String table, @Param("key") int key);

  default void createManagedSequences(@Param("key") int key) {
    MANAGED_SERIAL_TABLES.forEach(t -> createIdSequence(t, key));
  }

  /**
   * Updates the managed sequences for a given datasetKey to the current max of existing keys.
   * @param key datasetKey
   */
  default void updateManagedSequences(int key) {
    MANAGED_SERIAL_TABLES.forEach(t -> updateIdSequence(t, key));
  }

  default void deleteManagedSequences(@Param("key") int key) {
    MANAGED_SERIAL_TABLES.forEach(t -> deleteIdSequence(t, key));
  }

  /**
   * Deletes an entire partition if its dataset specific or deletes all data from shared partitions, e.g. for external datasets.
   * Leaves the dataset record itself untouched.
   *
   * @param key
   */
  default void delete(int key, DatasetOrigin origin) {
    deleteUsageCounter(key);
    if (origin.isManagedOrRelease()) {
      PROJECT_TABLES.forEach(t -> dropTable(t, key));
      Lists.reverse(TABLES).forEach(t -> dropTable(t, key));
      IDMAP_TABLES.forEach(t -> dropTable(t, key));
    } else {
      Lists.reverse(TABLES).forEach(t -> deleteData(t, key));
      SERIAL_TABLES.forEach(t -> deleteIdSequence(t, key));
    }
  }

  /**
   * Deletes all data from a table for the given datasetKey.
   * @param key datasetKey
   */
  void deleteData(@Param("table") String table, @Param("key") int key);

  void dropTable(@Param("table") String table, @Param("key") int key);

  void deleteUsageCounter(@Param("key") int key);
  
  /**
   * Attaches all dataset specific partition tables to their main table
   * so they become visible.
   *
   * Warning! This requires an AccessExclusiveLock on the main tables
   * which often leads to deadlocks, see https://github.com/Sp2000/colplus-backend/issues/387
   *
   * Best to manually acquire and afterwards release a lock first, attaching doesn't take long.
   *
   * @param key
   */
  default void attach(int key, DatasetOrigin origin) {
    if (origin.isManagedOrRelease()) {
      // create PKs
      TABLES.forEach(t -> createPk(t, key));
      // this also creates the indices from the partitioned table
      TABLES.forEach(t -> attachTable(t, key));

      final String suffix = String.valueOf(key);
      // create FKs
      // we always have a verbatim_key on every table
      TABLES.stream()
        .filter(t -> !t.equalsIgnoreCase("verbatim"))
        .forEach(t -> createFk(t, suffix, new FK("verbatim_key", "verbatim")));
      // custom fks
      FKS.forEach( (t,fks) -> fks.forEach(fk -> createFk(t, suffix, fk)));
      //
      attachTriggers(String.valueOf(key));
      // things specific to managed datasets only
      PROJECT_TABLES.forEach(t -> createPk(t, key));
      PROJECT_TABLES.forEach(t -> attachTable(t, key));
      createFk("verbatim_source", suffix, new FK("id", "name_usage", true, true));
    }
  }
  
  void attachTable(@Param("table") String table, @Param("key") int key);

  void detachTable(@Param("table") String table, @Param("key") int key);

  void createPk(@Param("table") String table, @Param("key") int key);

  /**
   * @param suffix table suffix, e.g. _1001 or _mod1
   */
  void createFk(@Param("table") String table,
                @Param("suffix") String suffix,
                @Param("fk") FK fk
  );

  /**
   * Attaches all required triggers for a given partition suffix.
   * Currently these are 1 trigger on the name and 2 triggers on the name usage partition.
   * Make sure to call this AFTER the partition table is attached.
   * @param suffix partition suffix, e.g. datasetkey for external datasets
   */
  void attachTriggers(@Param("key") String suffix);

  /**
   * Updates the name usage counter record with the current count.
   * Make sure to call this AFTER the partition table is attached
   * @param key datasetkey
   */
  int updateUsageCounter(@Param("key") int key);

  /**
   * Locks a dataset specific table in EXCLUSIVE mode, only allowing select statements by other transactions.
   * The lock is released when the transaction is ended. There is no other manual lock release possible.
   * Warning: this should never be used for EXTERNAL datasets which share partitions!!!
   */
  void lockTables(@Param("datasetKey") int datasetKey);

  /**
   * Checks whether the partition for the given datasetKey exists already.
   * @param key datasetKey
   * @return true if partition tables exist
   */
  default boolean exists(@Param("key") int key, DatasetOrigin origin) {
    if (origin.isManagedOrRelease()) {
      return existsDatasetSpecific(key);
    } else {
      return true;
    }
  }

  /**
   * @return if partitions exist specific only for the given dataset, i.e. managed or released.
   */
  boolean existsDatasetSpecific(@Param("key") int key);

  /**
   * Lists all dataset keys for which there is an existing name partition table
   */
  List<Integer> existingPartitions();

  /**
   * Return the list of columns for a given table igoring "doc" columns
   */
  List<String> columns(@Param("t") String table);
}
