package life.catalogue.orthvar;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrthographicVariationJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(OrthographicVariationJob.class);
  private final OrthographicVariationConsolidator hc;

  public OrthographicVariationJob(SqlSessionFactory factory, int datasetKey, int userKey) {
    super(datasetKey, userKey, JobPriority.MEDIUM);
    hc = OrthographicVariationConsolidator.entireDataset(factory, datasetKey);
  }

  public OrthographicVariationJob(SqlSessionFactory factory, int datasetKey, int userKey, String taxonID) {
    super(datasetKey, userKey, JobPriority.MEDIUM);
    List<SimpleName> families = new ArrayList<>();
    SimpleName root;
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(NameUsageMapper.class);
      var key = DSID.of(datasetKey, taxonID);
      root = mapper.getSimple(key);
      if (root == null) {
        throw NotFoundException.notFound(Taxon.class, key);
      } else if (!root.getStatus().isTaxon()) {
        throw new IllegalArgumentException("Root taxon is not an accepted name: " + taxonID);
      }

      TreeTraversalParameter params = TreeTraversalParameter.datasetNoSynonyms(datasetKey);
      params.setTaxonID(taxonID);
      params.setLowestRank(Rank.FAMILY);
      PgUtils.consume(()->mapper.processTreeSimple(params), sn -> {
        if (sn.getRank()==Rank.FAMILY && sn.getStatus().isTaxon()) {
          families.add(sn);
        }
      });
    }
    LOG.info("Found {} families to consolidate for root taxon {}", families.size(), root);
    hc = OrthographicVariationConsolidator.forTaxa(factory, datasetKey, families);
  }

  @Override
  protected void runWithLock() throws Exception {
    hc.consolidate();
  }
}
