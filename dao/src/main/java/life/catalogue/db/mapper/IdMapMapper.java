package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;

public interface IdMapMapper {
  String NAME_TBL = "idmap_name";
  String USAGE_TBL= "idmap_name_usage";

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

  int count(@Param("datasetKey") int datasetKey,
             @Param("table") String table
  );
  default int countName(int datasetKey) {
    return count(datasetKey, NAME_TBL);
  }
  default int countUsage(int datasetKey) {
    return count(datasetKey, USAGE_TBL);
  }

  String get(@Param("datasetKey") int datasetKey,
             @Param("table") String table,
             @Param("id") String id
  );
  default String getName(int datasetKey, String id) {
    return get(datasetKey, NAME_TBL, id);
  }
  default String getUsage(int datasetKey, String id) {
    return get(datasetKey, USAGE_TBL, id);
  }

}
