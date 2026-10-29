package life.catalogue.basgroup;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Groups homotypic names of a project or release, entirely or below a root taxon.
 * Unless told otherwise the name relations of the data are cleaned up first, as in an extended release,
 * so the grouping does not build on relations that contradict their names or each other, see {@link NameRelationCleaner}.
 */
public class HomotypicConsolidationJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(HomotypicConsolidationJob.class);
  private final SqlSessionFactory factory;
  private final HomotypicConsolidator hc;
  private final String taxonID;
  private final boolean cleanRelations;

  private HomotypicConsolidationJob(SqlSessionFactory factory, int datasetKey, int userKey, @Nullable String taxonID,
                                    boolean cleanRelations, HomotypicConsolidator hc) {
    super(datasetKey, userKey, JobPriority.MEDIUM);
    this.factory = factory;
    this.hc = hc;
    this.taxonID = taxonID;
    this.cleanRelations = cleanRelations;
    this.logToFile = true;
  }

  public HomotypicConsolidationJob(SqlSessionFactory factory, int datasetKey, int userKey) {
    this(factory, datasetKey, userKey, null, true);
  }

  public HomotypicConsolidationJob(SqlSessionFactory factory, int datasetKey, int userKey, String taxonID) {
    this(factory, datasetKey, userKey, taxonID, true);
  }

  /**
   * @param taxonID the root taxon to consolidate, null for the entire dataset
   * @param cleanRelations if true the name relations are cleaned up first, see {@link NameRelationCleaner}
   */
  public HomotypicConsolidationJob(SqlSessionFactory factory, int datasetKey, int userKey, @Nullable String taxonID,
                                   boolean cleanRelations) {
    this(factory, datasetKey, userKey, taxonID, cleanRelations, taxonID == null
      ? HomotypicConsolidator.entireDataset(factory, datasetKey)
      : buildHC(factory, datasetKey, taxonID)
    );
  }

  /**
   * @param taxonID the root taxon to consolidate, null when the entire dataset is consolidated.
   *                Without it those two very different jobs look the same in the history.
   * @param cleanRelations true if the name relations were cleaned up first
   */
  public record ConsolidationParams(int datasetKey, String taxonID, boolean cleanRelations) {
  }

  @Override
  public Object getParams() {
    return new ConsolidationParams(datasetKey, taxonID, cleanRelations);
  }

  /**
   * @return the accepted root taxon of a subtree
   * @throws NotFoundException if the taxon does not exist
   * @throws IllegalArgumentException if it is no accepted name
   */
  static SimpleName requireAcceptedRoot(NameUsageMapper mapper, int datasetKey, String taxonID) {
    var key = DSID.of(datasetKey, taxonID);
    var root = mapper.getSimple(key);
    if (root == null) {
      throw NotFoundException.notFound(Taxon.class, key);
    } else if (!root.getStatus().isTaxon()) {
      throw new IllegalArgumentException("Root taxon is not an accepted name: " + taxonID);
    }
    return root;
  }

  private static HomotypicConsolidator buildHC(SqlSessionFactory factory, int datasetKey, String taxonID) {
    List<SimpleName> families = new ArrayList<>();
    SimpleName root;
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(NameUsageMapper.class);
      root = requireAcceptedRoot(mapper, datasetKey, taxonID);

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
    return HomotypicConsolidator.forTaxa(factory, datasetKey, families);
  }

  @Override
  protected void runWithLock() throws Exception {
    if (cleanRelations) {
      setStep("cleaning name relations");
      new NameRelationCleaner(factory, datasetKey, taxonID).clean();
    }
    setStep("grouping homotypic names");
    hc.consolidate(Math.max(1, cfg.threads-1), getKey());
  }
}
