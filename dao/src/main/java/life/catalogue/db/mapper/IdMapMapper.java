package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;

public interface IdMapMapper {
  String NAME_TBL = "idmap_name";
  String USAGE_TBL= "idmap_name_usage";

  void createTable(@Param("datasetKey") int datasetKey, @Param("table") String table);

  default void createNameTable(int datasetKey) {
    createTable(datasetKey, NAME_TBL);
  }

  default void createUsageTable(int datasetKey) {
    createTable(datasetKey, USAGE_TBL);
  }

  void insert(@Param("datasetKey") int datasetKey,
              @Param("table") String table,
              @Param("id") String id,
              @Param("id2") String id2
  );

  default void mapName(int datasetKey, String id, String id2) {
    insert(datasetKey, NAME_TBL, id, id2);
  }

  default void mapUsage(int datasetKey, String id, String id2) {
    insert(datasetKey, USAGE_TBL, id, id2);
  }
}
