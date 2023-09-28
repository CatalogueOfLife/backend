package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.db.*;

import java.time.LocalDateTime;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

/**
 *
 */
public interface TypeMaterialMapper extends CRUD<DSID<String>, TypeMaterial>,
  DatasetProcessable<TypeMaterial>, SectorProcessable<TypeMaterial>, NameProcessable<TypeMaterial>, CopyDataset {

  /**
   * Deletes all type materials that have no linked name.
   * @param datasetKey the datasetKey to restrict the deletion to
   * @param before optional timestamp to restrict deletions to orphans before the given time
   * @return number of deleted type material records
   */
  int deleteOrphans(@Param("datasetKey") int datasetKey, @Param("before") @Nullable LocalDateTime before);
}
