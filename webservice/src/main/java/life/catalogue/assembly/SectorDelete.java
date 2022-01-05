package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.TempNameUsageRelated;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Deletes a sector but keeps its imports so we can still show historical releases properly which access the sync history of projects.
 * A sector deletion keeps synced data of rank above species level by default!
 * Names and taxa of ranks above species are kept, but the sectorKey is removed from all entities that previously belonged to the deleted sector.
 * At the same time all verbatim source records are removed
 */
public class SectorDelete extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDelete.class);
  private static final Rank maxAmbiguousRank = Arrays.stream(Rank.values()).filter(Rank::isAmbiguous).max(Rank::compareTo).orElseThrow(() -> new IllegalStateException("No ambiguous ranks exist"));
  private Rank cutoffRank = Rank.SPECIES;

  public SectorDelete(DSID<Integer> sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService, SectorDao dao, SectorImportDao sid,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, false, false, factory, indexService, dao, sid, successCallback, errorCallback, user);
  }

  @Override
  void doWork() {
    state.setState( ImportState.DELETING);
    deleteSector(sectorKey);
  }

  @Override
  void doMetrics() throws Exception {
    // we don't remove any sector metric anymore to avoid previous releases to be broken
    // see https://github.com/CatalogueOfLife/backend/issues/986

    //try {
    //  sid.deleteAll(sectorKey);
    //} catch (IOException e) {
    //  LOG.error("Failed to delete metrics files for sector {}", sectorKey, e);
    //}
  }

  @Override
  void updateSearchIndex() throws Exception {
    indexService.deleteSector(sectorKey);
    LOG.info("Removed sector {} from search index", sectorKey);
  }

  private void deleteSector(DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession(false)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);

      // create a temp table that holds usage and name ids to be deleted - we need to be flexible keeping some
      // temp tables will be visible only to this session and removed by postgres once the session is closed
      um.createTempTable();
      // add all synonyms from the sector to the temp table
      um.addSectorSynonymsToTemp(sectorKey);
      // add all usages below genus from the sector to the temp table
      um.addSectorBelowRankToTemp(sectorKey, Rank.SUBGENUS);
      // remove ambiguous zoological ranks from the temp table so they dont get deleted - they might be higher ranks to keep!
      removeZoologicalAmbiguousRanks(sectorKey, session);

      // delete usages, names and related records that are listed in the temp table
      // order matters!
      List<TempNameUsageRelated> mappers = List.of(
        // usage related
        session.getMapper(DistributionMapper.class),
        session.getMapper(MediaMapper.class),
        session.getMapper(VernacularNameMapper.class),
        session.getMapper(SpeciesInteractionMapper.class),
        session.getMapper(TaxonConceptRelationMapper.class),
        session.getMapper(TreatmentMapper.class),
        // usage
        session.getMapper(NameUsageMapper.class),
        // name related
        session.getMapper(NameMatchMapper.class),
        session.getMapper(TypeMaterialMapper.class),
        session.getMapper(NameRelationMapper.class),
        // name
        session.getMapper(NameMapper.class)
      );

      mappers.forEach(m -> {
        int count = m.deleteByTemp(sectorKey.getDatasetKey());
        String type = m.getClass().getSimpleName().replace("Mapper", "");
        LOG.info("Deleted {} {} records from sector {}", count, type, sectorKey);
        session.commit();
      });
      session.commit();

      // TODO: remove refs and name rels

      // remove verbatim sources from remaining usages
      vsm.deleteBySector(s);

      // remove sector from usages, names, refs & type_material
      int count = um.removeSectorKey(sectorKey);
      nm.removeSectorKey(sectorKey);
      session.getMapper(ReferenceMapper.class).removeSectorKey(sectorKey);
      session.getMapper(TypeMaterialMapper.class).removeSectorKey(sectorKey);
      LOG.info("Mark {} existing taxa with their synonyms and related information to not belong to sector {} anymore", count, sectorKey);

      // update datasetSectors counts
      SectorDao.incSectorCounts(session, s, -1);
      // remove imports and sector itself
      session.getMapper(SectorImportMapper.class).delete(sectorKey);
      session.getMapper(SectorMapper.class).delete(sectorKey);
      session.commit();

      LOG.info("Deleted sector {}, keeping usages above {} level", sectorKey, cutoffRank);
    }
  }

  private void removeZoologicalAmbiguousRanks(DSID<Integer> sectorKey, SqlSession session) {
    int counter = 0;
    List<String> nids = session.getMapper(NameMapper.class).ambiguousRankNameIds(sectorKey.getDatasetKey(), sectorKey.getId());
    LOG.debug("Found {} names of ambiguous ranks. Check their usages next", nids.size());
    // if we have ambiguous ranks filter out the ones that have children of ranks above SUPERSECTION
    // we iterate over children as we rarely even get ambiguous ranks
    if (nids != null && !nids.isEmpty()) {
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      for (String nid : nids) {
        usageLoop:
        for (NameUsageBase u : um.listByNameID(sector.getDatasetKey(), nid, new Page(0, 1000))){
          for (SimpleName sn : um.processTreeSimple(sector.getDatasetKey(), sector.getId(), u.getId(), null, Rank.INFRAGENERIC_NAME, false)) {
            if (sn.getRank().higherThan(maxAmbiguousRank)) {
              um.removeFromTemp(nid);
              counter++;
              break usageLoop;
            }
          }
        }
      }
    }
    LOG.info("Found {} ambiguous zoological ranks above genus from sector {} that we will keep", counter, sectorKey);
  }

}
