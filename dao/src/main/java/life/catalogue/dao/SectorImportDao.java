package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.*;
import life.catalogue.db.mapper.SectorImportMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

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
    si.setMediaCount(mapper.countMedia(datasetKey, key));
    si.setNameCount(mapper.countName(datasetKey, key));
    si.setReferenceCount(mapper.countReference(datasetKey, key));
    si.setSynonymCount(mapper.countSynonym(datasetKey, key));
    si.setTaxonCount(mapper.countTaxon(datasetKey, key));
    si.setTreatmentCount(mapper.countTreatment(datasetKey, key));
    si.setTypeMaterialCount(mapper.countTypeMaterial(datasetKey, key));
    si.setVernacularCount(mapper.countVernacular(datasetKey, key));

    si.setDistributionsByGazetteerCount(DatasetImportDao.countMap(Gazetteer.class, mapper.countDistributionsByGazetteer(datasetKey, key)));
    si.setExtinctTaxaByRankCount(DatasetImportDao.countMap(DatasetImportDao::parseRank, mapper.countExtinctTaxaByRank(datasetKey, key)));
    si.setIssuesCount(DatasetImportDao.countMap(Issue.class, mapper.countIssues(datasetKey, key)));
    si.setMediaByTypeCount(DatasetImportDao.countMap(MediaType.class, mapper.countMediaByType(datasetKey, key)));
    si.setNameRelationsByTypeCount(DatasetImportDao.countMap(NomRelType.class, mapper.countNameRelationsByType(datasetKey, key)));
    si.setNamesByCodeCount(DatasetImportDao.countMap(NomCode.class, mapper.countNamesByCode(datasetKey, key)));
    si.setNamesByRankCount(DatasetImportDao.countMap(DatasetImportDao::parseRank, mapper.countNamesByRank(datasetKey, key)));
    si.setNamesByStatusCount(DatasetImportDao.countMap(NomStatus.class, mapper.countNamesByStatus(datasetKey, key)));
    si.setNamesByTypeCount(DatasetImportDao.countMap(NameType.class, mapper.countNamesByType(datasetKey, key)));
    si.setSynonymsByRankCount(DatasetImportDao.countMap(DatasetImportDao::parseRank, mapper.countSynonymsByRank(datasetKey, key)));
    si.setTaxaByRankCount(DatasetImportDao.countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(datasetKey, key)));
    si.setTaxonRelationsByTypeCount(DatasetImportDao.countMap(TaxRelType.class, mapper.countTaxonRelationsByType(datasetKey, key)));
    si.setTypeMaterialByStatusCount(DatasetImportDao.countMap(TypeStatus.class, mapper.countTypeMaterialByStatus(datasetKey, key)));
    si.setUsagesByOriginCount(DatasetImportDao.countMap(Origin.class, mapper.countUsagesByOrigin(datasetKey, key)));
    si.setUsagesByStatusCount(DatasetImportDao.countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(datasetKey, key)));
    si.setVernacularsByLanguageCount(DatasetImportDao.countMap(mapper.countVernacularsByLanguage(datasetKey, key)));
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
