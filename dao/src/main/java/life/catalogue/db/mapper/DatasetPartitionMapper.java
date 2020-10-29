package life.catalogue.db.mapper;

import com.google.common.collect.Lists;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

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
      "taxon_rel",
      "distribution",
      "media",
      "treatment",
      "vernacular_name"
  );
  
  List<String> SERIAL_TABLES = Lists.newArrayList(
      "verbatim",
      "name_rel",
      "taxon_rel",
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
      new FK("published_in_id", "reference"),
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
    "taxon_rel", List.of(
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
   * Creates a new dataset partition for all data tables if not already existing.
   * The tables are not attached to the main partitioned table yet, see attach().
   * Indices are not created yet which should happen after the data is inserted.
   * Tables with integer id columns will have their own sequence.
   */
  default void create(int key) {
    TABLES.forEach(t -> createTable(t, key));
    SERIAL_TABLES.forEach(t -> createSerial(t, key));
    // estimates can exist also in non managed datasets, so we need to have an id sequence for them in all datasets
    // but they are not a partitioned table, treat them special
    createIdSequence("estimate", key);
  }
  
  void createTable(@Param("table") String table, @Param("key") int key);

  void createIdMapTable(@Param("table") String table, @Param("key") int key);

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

  void updateManagedSequence(@Param("table") String table, @Param("key") int key);

  /**
   * Updates the managed sequences for a given datasetKey to the current max of existing keys.
   * @param key datasetKey
   */
  default void updateManagedSequences(int key) {
    MANAGED_SERIAL_TABLES.forEach(t -> updateManagedSequence(t, key));
  }

  default void deleteManagedSequences(@Param("key") int key) {
    MANAGED_SERIAL_TABLES.forEach(t -> deleteIdSequence(t, key));
  }

  /**
   * Deletes a dataset partition from all data tables if existing, but leaves the dataset itself untouched
   *
   * @param key
   */
  default void delete(int key) {
    deleteUsageCounter(key);
    Lists.reverse(TABLES).forEach(t -> deleteTable(t, key));
    IDMAP_TABLES.forEach(t -> deleteTable(t, key));
  }
 
  void deleteTable(@Param("table") String table, @Param("key") int key);

  void deleteUsageCounter(@Param("key") int key);
  
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
    // create PKs
    TABLES.forEach(t -> createPk(t, key));
    // this also creates the indices from the partitioned table
    TABLES.forEach(t -> attachTable(t, key));
    // create FKs
    // we always have a verbatim_key on every table
    TABLES.stream()
      .filter(t -> !t.equalsIgnoreCase("verbatim"))
      .forEach(t -> createFk(t, key, new FK("verbatim_key", "verbatim")));
    // custom fks
    FKS.forEach( (t,fks) -> fks.forEach(fk -> createFk(t, key, fk)));
  }
  
  void attachTable(@Param("table") String table, @Param("key") int key);

  void detachTable(@Param("table") String table, @Param("key") int key);

  void createPk(@Param("table") String table, @Param("key") int key);

  void createFk(@Param("table") String table,
                @Param("key") int key,
                @Param("fk") FK fk
  );

  /**
   * Attaches a trigger to the name usage partition that tracks the total counts of usages.
   * Make sure to call this AFTER the partition table is attached
   * @param key datasetkey
   */
  void attachUsageCounter(@Param("key") int key);

  int updateUsageCounter(@Param("key") int key);

  /**
   * Locks a dataset specific table in EXCLUSIVE mode, only allowing select statements by other transactions.
   * The lock is released when the transaction is ended. There is no other manual lock release possible.
   */
  void lockTables(@Param("datasetKey") int datasetKey);

  /**
   * Checks whether the partition for the given datasetKey exists already.
   * @param key datasetKey
   * @return true if partition tables exist
   */
  boolean exists(@Param("key") int key);

  /**
   * Lists all dataset keys for which there is an existing name partition table
   */
  List<Integer> existingPartitions();
}
