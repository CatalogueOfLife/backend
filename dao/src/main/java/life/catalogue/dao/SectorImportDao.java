package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.*;
import life.catalogue.db.mapper.SectorImportMapper;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.dao.DatasetImportDao.countMap;
public class SectorImportDao {

  private static final Logger LOG = LoggerFactory.getLogger(SectorImportDao.class);

  private final SqlSessionFactory factory;
  private final FileMetricsSectorDao fileMetricsDao;

  public SectorImportDao(SqlSessionFactory factory, File repo) {
    this.factory = factory;
    this.fileMetricsDao = new FileMetricsSectorDao(factory, repo);
  }

  /**
   * Update all metrics for the given sector import and sector key.
   * The key is given explicitly because it might deviate from the datasetKey of the SectorImport
   * in case of releases, where we store the release metrics in the mother project (which is the SectorImport.datasetKey).
   *
   * @param si import to update
   * @param datasetKey the dataset key to analyze the data from. Should be the releases datasetKey if its a release
   */
  public void updateMetrics(SectorImport si, int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper mapper = session.getMapper(SectorImportMapper.class);
      populateCounts(mapper, si, datasetKey);
      mapper.update(si);

      DSID<Integer> dataKey = DSID.of(datasetKey, si.getSectorKey());
      fileMetricsDao.updateTree(dataKey, si.getSectorDSID(), si.getAttempt());
      fileMetricsDao.updateNames(dataKey, si.getSectorDSID(), si.getAttempt());

    } catch (IOException e) {
      LOG.error("Failed to update metrics for sector {} from dataset {}", si.getSectorDSID(), datasetKey, e);
    }
  }


  private void populateCounts(SectorImportMapper mapper, SectorImport si, int datasetKey) {
    final int key = si.getSectorKey();

    si.setBareNameCount(mapper.countBareName(datasetKey, key));
    si.setDistributionCount(mapper.countDistribution(datasetKey, key));
    si.setEstimateCount(mapper.countEstimate(datasetKey, key));
    si.setMediaCount(mapper.countMedia(datasetKey, key));
    si.setNameCount(mapper.countName(datasetKey, key));
    si.setReferenceCount(mapper.countReference(datasetKey, key));
    si.setSynonymCount(mapper.countSynonym(datasetKey, key));
    si.setTaxonCount(mapper.countTaxon(datasetKey, key));
    si.setTreatmentCount(mapper.countTreatment(datasetKey, key));
    si.setTypeMaterialCount(mapper.countTypeMaterial(datasetKey, key));
    si.setVernacularCount(mapper.countVernacular(datasetKey, key));

    si.setDistributionsByGazetteerCount(countMap(Gazetteer.class, mapper.countDistributionsByGazetteer(datasetKey, key)));
    si.setExtinctTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countExtinctTaxaByRank(datasetKey, key)));
    si.setIssuesCount(countMap(Issue.class, mapper.countIssues(datasetKey, key)));
    si.setMediaByTypeCount(countMap(MediaType.class, mapper.countMediaByType(datasetKey, key)));
    si.setNameRelationsByTypeCount(countMap(NomRelType.class, mapper.countNameRelationsByType(datasetKey, key)));
    si.setNamesByCodeCount(countMap(NomCode.class, mapper.countNamesByCode(datasetKey, key)));
    si.setNamesByRankCount(countMap(DatasetImportDao::parseRank, mapper.countNamesByRank(datasetKey, key)));
    si.setNamesByStatusCount(countMap(NomStatus.class, mapper.countNamesByStatus(datasetKey, key)));
    si.setNamesByTypeCount(countMap(NameType.class, mapper.countNamesByType(datasetKey, key)));
    si.setSpeciesInteractionsByTypeCount(countMap(SpeciesInteractionType.class, mapper.countSpeciesInteractionsByType(datasetKey, key)));
    si.setSynonymsByRankCount(countMap(DatasetImportDao::parseRank, mapper.countSynonymsByRank(datasetKey, key)));
    si.setTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(datasetKey, key)));
    si.setTaxonConceptRelationsByTypeCount(countMap(TaxonConceptRelType.class, mapper.countTaxonConceptRelationsByType(datasetKey, key)));
    si.setTypeMaterialByStatusCount(countMap(TypeStatus.class, mapper.countTypeMaterialByStatus(datasetKey, key)));
    si.setUsagesByOriginCount(countMap(Origin.class, mapper.countUsagesByOrigin(datasetKey, key)));
    si.setUsagesByStatusCount(countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(datasetKey, key)));
    si.setVernacularsByLanguageCount(countMap(mapper.countVernacularsByLanguage(datasetKey, key)));
  }

  public void deleteAll(DSID<Integer> sectorKey) throws IOException {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(SectorImportMapper.class).delete(sectorKey);
    }
    fileMetricsDao.deleteAll(sectorKey);
  }

  public SectorImport getAttempt(DSID<Integer> sectorKey, int attempt) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(SectorImportMapper.class).get(sectorKey, attempt);
    }
  }
}
