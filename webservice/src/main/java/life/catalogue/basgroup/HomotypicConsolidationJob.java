package life.catalogue.basgroup;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomotypicConsolidationJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(HomotypicConsolidationJob.class);
  private final HomotypicConsolidator hc;

  public HomotypicConsolidationJob(SqlSessionFactory factory, int datasetKey, int userKey) {
    super(datasetKey, userKey, JobPriority.MEDIUM);
    hc = HomotypicConsolidator.forAllFamilies(factory, datasetKey);
  }

  public HomotypicConsolidationJob(SqlSessionFactory factory, int datasetKey, int userKey, String taxonID) {
    super(datasetKey, userKey, JobPriority.MEDIUM);
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(NameUsageMapper.class);
      var key = DSID.of(datasetKey, taxonID);
      var root = mapper.getSimple(key);
      if (root == null) {
        throw NotFoundException.notFound(Taxon.class, key);
      } else if (!root.getStatus().isTaxon()) {
        throw new IllegalArgumentException("Root taxon is not an accepted name: " + taxonID);
      }
      var families = mapper.findSimple(datasetKey, null, TaxonomicStatus.ACCEPTED, Rank.FAMILY, null);
      var familiesProv = mapper.findSimple(datasetKey, null, TaxonomicStatus.PROVISIONALLY_ACCEPTED, Rank.FAMILY, null);
      families.addAll(familiesProv);
      LOG.info("Found {} families to consolidate for root taxon {}", families.size(), root);
      hc = HomotypicConsolidator.forFamilies(factory, datasetKey, families);
    }
  }

  @Override
  protected void runWithLock() throws Exception {
    hc.consolidate();
  }
}
