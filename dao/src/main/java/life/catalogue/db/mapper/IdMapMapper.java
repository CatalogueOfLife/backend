package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;

public interface IdMapMapper {

  default void mapName(int datasetKey, String id, String id2) {
    insert(datasetKey, "idmap_name", id, id2);
  }

  default void mapUsage(int datasetKey, String id, String id2) {
    insert(datasetKey, "idmap_name_usage", id, id2);
  }

  void insert(@Param("datasetKey") int datasetKey,
              @Param("table") String table,
              @Param("id") String id,
              @Param("id2") String id2
  );
}
