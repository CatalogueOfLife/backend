package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.CitationMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorMetadataMapper;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the optional metadata of a sector, and resolves what a sector page should show.
 * See issue #1273 and docs/2026-08-27-sector-metadata.md.
 *
 * Resolution layers what the sector says on top of what it inherits, so the vast majority of sectors -
 * which say nothing at all - simply render their source dataset's metadata:
 *
 * <pre>
 * project: source dataset (already dataset_patch'ed)  +  publisher declared  +  editor override
 * release: dataset_source (already frozen)            +  the delta frozen at release time
 * </pre>
 */
public class SectorMetadataDao {
  private static final Logger LOG = LoggerFactory.getLogger(SectorMetadataDao.class);
  private final SqlSessionFactory factory;
  private final DatasetSourceDao sourceDao;

  public SectorMetadataDao(SqlSessionFactory factory, DatasetSourceDao sourceDao) {
    this.factory = factory;
    this.sourceDao = sourceDao;
  }

  /**
   * The raw, sparse layer this dataset stores about the sector, without anything it inherits.
   * @return null if the sector says nothing of its own
   */
  public Dataset getPatch(DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(SectorMetadataMapper.class).get(sectorKey);
    }
  }

  /**
   * Upserts the sector's own metadata layer, citations included. Replaces whatever was there:
   * a patch is a whole document, not a delta on a delta.
   */
  public void putPatch(DSID<Integer> sectorKey, Dataset patch, int user) {
    try (SqlSession session = factory.openSession(false)) {
      SectorMetadataMapper smm = session.getMapper(SectorMetadataMapper.class);
      CitationMapper cm = session.getMapper(CitationMapper.class);
      patch.setModifiedBy(user);
      if (smm.get(sectorKey) == null) {
        patch.setCreatedBy(user);
        smm.create(sectorKey, patch);
      } else {
        smm.update(sectorKey, patch);
      }
      cm.deleteSector(sectorKey.getDatasetKey(), sectorKey.getId());
      if (patch.getSource() != null) {
        for (var c : patch.getSource()) {
          cm.createSector(sectorKey.getDatasetKey(), sectorKey.getId(), c);
        }
      }
      session.commit();
    }
  }

  public void deletePatch(DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession(false)) {
      session.getMapper(CitationMapper.class).deleteSector(sectorKey.getDatasetKey(), sectorKey.getId());
      session.getMapper(SectorMetadataMapper.class).delete(sectorKey);
      session.commit();
    }
  }

  /**
   * What a sector page shows: the sector's metadata with everything it does not say inherited from its
   * source dataset. Never null for an existing sector - a sector without any metadata of its own
   * resolves to exactly its source dataset's metadata.
   *
   * @return null only if the sector itself does not exist
   */
  public Dataset resolve(DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession()) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) return null;
      return resolve(s, session);
    }
  }

  /**
   * @param s the sector to resolve, already loaded
   */
  public Dataset resolve(Sector s, SqlSession session) {
    final DatasetOrigin origin = DatasetInfoCache.CACHE.info(s.getDatasetKey()).origin;
    Dataset base = base(s, origin, session);

    SectorMetadataMapper smm = session.getMapper(SectorMetadataMapper.class);
    // In a release the publisher declared layer was already merged into the sector's own row when the
    // release was built, so applying it again here would resolve against a source that has since moved on.
    if (!origin.isRelease() && s.getSubjectSectorKey() != null) {
      apply(base, smm.get(s.getSubjectSectorKey()));
    }
    apply(base, smm.get(DSID.of(s.getDatasetKey(), s.getId())));
    return base;
  }

  /**
   * What the sector inherits before it says anything itself.
   */
  private Dataset base(Sector s, DatasetOrigin origin, SqlSession session) {
    if (s.getSubjectDatasetKey() == null) {
      // a SOURCE sector: an external dataset talking about a part of its own data, so the umbrella
      // dataset itself is what it refines
      return session.getMapper(DatasetMapper.class).get(s.getDatasetKey());
    }
    // for a project this applies the project's dataset_patch, for a release it reads the frozen
    // dataset_source. Either way the container fields are computed along with it, so a sector inherits
    // the release as its container exactly like a source dataset does.
    Dataset base = sourceDao.get(s.getDatasetKey(), s.getSubjectDatasetKey(), false);
    if (base == null) {
      LOG.warn("Sector {} of dataset {} has no source dataset {} to inherit metadata from",
        s.getId(), s.getDatasetKey(), s.getSubjectDatasetKey());
      base = new Dataset();
    }
    return base;
  }

  /**
   * The merged sector level delta - publisher declared then editor override - as one sparse document.
   * This is what a release freezes, so that re-importing the source afterwards cannot change what an
   * already published release renders.
   *
   * @return null if the sector has nothing of its own to freeze
   */
  public @Nullable Dataset mergedDelta(Sector s, SqlSession session) {
    SectorMetadataMapper smm = session.getMapper(SectorMetadataMapper.class);
    Dataset declared = s.getSubjectSectorKey() == null ? null : smm.get(s.getSubjectSectorKey());
    Dataset own = smm.get(DSID.of(s.getDatasetKey(), s.getId()));
    if (declared == null) return own;
    if (own == null) return declared;

    Dataset merged = new Dataset(declared);
    apply(merged, own);
    return merged;
  }

  /**
   * Layers a sparse patch over a base. Empty collections on the patch are ignored by
   * {@link Dataset#applyPatch(Dataset)}, so a layer that simply has no keywords or citations inherits
   * them rather than wiping them.
   */
  private void apply(Dataset base, @Nullable Dataset patch) {
    if (patch == null) return;
    base.applyPatch(patch);
    // license is excluded from Dataset.PATCH_PROPS as "required", so applyPatch never carries it.
    // A sub source routinely licenses differently from its umbrella, so apply it explicitly.
    if (patch.getLicense() != null) {
      base.setLicense(patch.getLicense());
    }
  }

  /**
   * The ids of the sectors of a dataset that have metadata of their own. Most have none.
   */
  public List<Integer> listSectorIdsWithMetadata(int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(SectorMetadataMapper.class).listSectorIds(datasetKey);
    }
  }
}
