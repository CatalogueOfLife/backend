package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.TempNameUsageRelated;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.UsageMatcherGlobal;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

/**
 * Deletes a sector but keeps its imports so we can still show historical releases properly which access the sync history of projects.
 * A sector deletion keeps synced data of rank above species level by default!
 * Names and taxa of ranks above species are kept, but the sectorKey is removed from all entities that previously belonged to the deleted sector.
 * At the same time all verbatim source records and other associated data like vernacular names are entirely removed.
 */
public class SectorDelete extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDelete.class);
  private static final Rank maxAmbiguousRank = Arrays.stream(Rank.values()).filter(Rank::isAmbiguous).max(Rank::compareTo).orElseThrow(() -> new IllegalStateException("No ambiguous ranks exist"));
  private Rank cutoffRank = Rank.SPECIES;

  SectorDelete(DSID<Integer> sectorKey, SqlSessionFactory factory, UsageMatcherGlobal matcher, NameUsageIndexService indexService, SectorDao dao, SectorImportDao sid, EventBus bus,
               Consumer<SectorRunnable> successCallback,
               BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    super(sectorKey, false, false, true, factory, matcher, indexService, dao, sid, bus, successCallback, errorCallback, user);
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
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);

      // create a temp table that holds usage and name ids to be deleted - we need to be flexible keeping some
      // temp tables will be visible only to this session and removed by postgres once the session is closed
      LOG.info("Create temporary deletion table for sector {}", sectorKey);
      um.createTempTable();
      // add all synonyms from the sector to the temp table
      um.addSectorSynonymsToTemp(sectorKey);
      // add all accepted usages below genus from the sector to the temp table
      um.addSectorBelowRankToTemp(sectorKey, Rank.SUBGENUS);
      // index temp table
      um.indexTempTable();
      // remove ambiguous zoological ranks from the temp table so they dont get deleted - they might be higher ranks to keep!
      removeZoologicalAmbiguousRanks(sectorKey, session);

      // delete usages, names and related records that are listed in the temp table
      LOG.info("Delete records using temporary deletion table for sector {}", sectorKey);
      for (Class<? extends TempNameUsageRelated> mc : TempNameUsageRelated.MAPPERS) {
        TempNameUsageRelated m = session.getMapper(mc);
        int count = m.deleteByTemp(sectorKey.getDatasetKey());
        String type = m.getClass().getSimpleName().replace("Mapper", "");
        LOG.info("Deleted {} {} records from sector {}", count, type, sectorKey);
      }
      // the commit removes the temp table!!!
      session.commit();

      // TODO: remove refs and name rels

      // remove verbatim sources from remaining usages
      vsm.deleteBySector(s);

      // remove sector from all entities left
      SectorProcessable.MAPPERS.forEach(mc -> {
        int count;
        SectorProcessable m = session.getMapper(mc);
        if (mc.equals(VerbatimSourceMapper.class)) {
          count = m.deleteBySector(sectorKey);
          LOG.info("Deleted {} verbatim sources for sector {}", count, sectorKey);
        } else {
          count = m.removeSectorKey(sectorKey);
          String type = mc.getSimpleName().replace("Mapper", "");
          LOG.info("Removed sector key {} from {} {} records", sectorKey, count, type);
        }
      });

      // update datasetSectors counts
      SectorDao.incSectorCounts(session, s, -1);
      // remove sector itself
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
          TreeTraversalParameter ttp = TreeTraversalParameter.sectorTarget(sector);
          ttp.setTaxonID(u.getId());
          ttp.setLowestRank(Rank.INFRAGENERIC_NAME);
          ttp.setSynonyms(false);
          try (var cursor = um.processTreeSimple(ttp)) {
            for (SimpleName sn : cursor) {
              if (sn.getRank().higherThan(maxAmbiguousRank)) {
                um.removeFromTemp(nid);
                counter++;
                break usageLoop;
              }
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
    LOG.info("Found {} ambiguous zoological ranks above genus from sector {} that we will keep", counter, sectorKey);
  }

}
