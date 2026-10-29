package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TypeDesignation;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.api.model.TypifiedName;
import life.catalogue.db.*;
import life.catalogue.db.NameBatchable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

/**
 * Type specimens in table type_material together with the names they typify in table type_material_name.
 * A specimen can typify several names, each with its own status, so all name based methods
 * work on the links and only remove a specimen once it typifies no name anymore.
 */
public interface TypeMaterialMapper extends CRUD<DSID<String>, TypeMaterial>,
  DatasetProcessable<TypeMaterial>, SectorProcessable<TypeMaterial>, NameProcessable<TypeMaterial>,
  NameBatchable<TypeMaterial>, CopyDataset {

  /**
   * @return the type designations of a name, each with the specimen and its status for that name
   */
  default List<TypeDesignation> listDesignations(DSID<String> nameKey) {
    return listByName(nameKey).stream()
      .map(tm -> TypeDesignation.of(nameKey.getId(), tm))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  /**
   * Creates the specimen and all its name links.
   */
  @Override
  default void create(TypeMaterial obj) {
    createSpecimen(obj);
    if (!obj.getNames().isEmpty()) {
      createNames(obj);
    }
  }

  /**
   * Updates the specimen and replaces all its name links.
   */
  @Override
  default int update(TypeMaterial obj) {
    int x = updateSpecimen(obj);
    deleteNames(obj);
    if (!obj.getNames().isEmpty()) {
      createNames(obj);
    }
    return x;
  }

  void createSpecimen(TypeMaterial obj);

  int updateSpecimen(TypeMaterial obj);

  /**
   * Inserts all name links of the given specimen.
   */
  void createNames(TypeMaterial obj);

  /**
   * Adds a single name link to an existing specimen.
   */
  void createName(@Param("datasetKey") int datasetKey, @Param("id") String typeMaterialId, @Param("tn") TypifiedName name);

  /**
   * Removes all name links of the given specimen.
   */
  void deleteNames(TypeMaterial obj);

  /**
   * Removes the links to the name and the specimens which typify no other name.
   * @return number of deleted specimens
   */
  @Override
  default int deleteByName(DSID<String> key) {
    int x = deleteSpecimensByName(key);
    deleteLinksByName(key);
    return x;
  }

  int deleteSpecimensByName(@Param("key") DSID<String> key);

  int deleteLinksByName(@Param("key") DSID<String> key);

  /**
   * Removes the links to the names in the temp table and the specimens which typify no other name.
   * @return number of deleted specimens
   */
  @Override
  default int deleteByTemp(int datasetKey) {
    int x = deleteSpecimensByTemp(datasetKey);
    deleteLinksByTemp(datasetKey);
    return x;
  }

  int deleteSpecimensByTemp(@Param("datasetKey") int datasetKey);

  int deleteLinksByTemp(@Param("datasetKey") int datasetKey);

  /**
   * Copies the specimens and their name links, the latter optionally mapping the name ids.
   * @return number of copied specimens
   */
  @Override
  default int copyDataset(int datasetKey, int newDatasetKey, boolean mapIds) {
    int x = copySpecimens(datasetKey, newDatasetKey);
    copyNames(datasetKey, newDatasetKey, mapIds);
    return x;
  }

  int copySpecimens(@Param("datasetKey") int datasetKey, @Param("newDatasetKey") int newDatasetKey);

  int copyNames(@Param("datasetKey") int datasetKey, @Param("newDatasetKey") int newDatasetKey, @Param("mapIds") boolean mapIds);

  /**
   * Deletes all name links to missing names and then all type materials that have no linked name.
   * @param datasetKey the datasetKey to restrict the deletion to
   * @param before optional timestamp to restrict deletions to orphans before the given time
   * @return number of deleted type material records
   */
  default int deleteOrphans(int datasetKey, @Nullable LocalDateTime before) {
    deleteOrphanLinks(datasetKey);
    return deleteOrphanSpecimens(datasetKey, before);
  }

  int deleteOrphanLinks(@Param("datasetKey") int datasetKey);

  int deleteOrphanSpecimens(@Param("datasetKey") int datasetKey, @Param("before") @Nullable LocalDateTime before);

  /**
   * @return true if there is at least one type material record in the given dataset
   */
  boolean entityExists(@Param("datasetKey") int datasetKey);
}
