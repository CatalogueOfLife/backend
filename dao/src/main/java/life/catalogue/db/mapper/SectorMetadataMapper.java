package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Optional, dataset-like metadata attached to a sector so it can render as a sub source page,
 * the way a source dataset does. See issue #1273.
 *
 * A row is always sparse and carries applyPatch semantics: whatever it does not say is inherited.
 * One table serves all three stages, told apart only by the origin of the datasetKey:
 * <ul>
 *   <li>EXTERNAL - declared by the publisher, rewritten on every import</li>
 *   <li>PROJECT - the editor's override</li>
 *   <li>RELEASE - the two above merged and frozen at release time</li>
 * </ul>
 *
 * The carrier is {@link Dataset}, exactly as it is for {@link DatasetPatchMapper}: a patch has never had
 * a model of its own, and {@link Dataset#applyPatch(Dataset)} is the merge. Note the returned Dataset
 * has a null key - it describes a sector, not a dataset, and the caller already knows which sector it
 * asked for.
 *
 * Deliberately not a {@link life.catalogue.db.DatasetProcessable}: that interface would enrol this
 * mapper in DatasetDao's bulk delete loop, which runs before the sector retention decision and would
 * drop the metadata of public releases whose sectors are being kept.
 */
public interface SectorMetadataMapper {

  /**
   * @param key datasetKey plus the sector id
   */
  Dataset get(@Param("key") DSID<Integer> key);

  void create(@Param("key") DSID<Integer> key, @Param("obj") Dataset obj);

  int update(@Param("key") DSID<Integer> key, @Param("obj") Dataset obj);

  int delete(@Param("key") DSID<Integer> key);

  /**
   * The ids of all sectors of a dataset that have metadata of their own, ascending.
   * Deliberately not a full listing: most sectors have no row at all, and callers such as the release
   * archiving only need to know which few to visit.
   */
  List<Integer> listSectorIds(@Param("datasetKey") int datasetKey);

  int deleteByDataset(@Param("datasetKey") int datasetKey);

  /**
   * The ids of the sectors of a release that can possibly have a metadata delta to freeze, i.e. those
   * that either carry the project's own layer or link to a publisher declared one. A project has tens of
   * thousands of sectors and almost none of them have metadata, so the release must not visit them all.
   *
   * @param datasetKey the release
   * @param projectKey where the sector's own layer lives
   */
  List<Integer> listSectorIdsToFreeze(@Param("datasetKey") int datasetKey, @Param("projectKey") int projectKey);
}
