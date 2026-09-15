package life.catalogue.db.mapper;

import life.catalogue.api.model.SimpleName;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

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

  /**
   * Fills the name id mapping table from the usage one, giving every name the stable id of one of its own usages.
   * Names are not matched a second time: their identity is entirely derived from the usages that carry them, so they
   * inherit every stability property the usage id mapping has.
   *
   * A name with several usages - pro parte synonyms, or the same name accepted in one branch and a synonym in
   * another - picks exactly one of them, preferring the usage that already was this name's id in the previous
   * release so the name id does not move when its accepted usage is sunk into synonymy.
   *
   * Run this after the usage ids have been mapped. Bare names have no usage to borrow from and keep their original
   * id, which for a project release is normally moot: removeBareNames deletes them before the mapping.
   *
   * @param datasetKey the mapped dataset, i.e. the project or the temp project of an extended release
   * @param prevReleaseKey the previous release of the same origin, or null if there is none
   * @return number of mapped names
   */
  int mapNamesFromUsages(@Param("datasetKey") int datasetKey, @Nullable @Param("prevReleaseKey") Integer prevReleaseKey);

  /**
   * Iterates over all usages of the dataset that have no entry in its usage id mapping table
   * and therefore keep their original id when the dataset is copied with mapped ids.
   */
  Cursor<SimpleName> processUnmappedUsages(@Param("datasetKey") int datasetKey);

}
