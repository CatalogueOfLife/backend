package life.catalogue.matching.taxonomic;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import java.util.Objects;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jonathan Rees Listtool integration
 * https://github.com/jar398/listtools
 * https://github.com/gbif/gbif-docker-images/blob/feature/checklist-image/checklist-tools/Dockerfile
 */
public class TaxonomicAlignJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonomicAlignJob.class);
  private final int datasetKey1;
  private final String root1;
  private final int datasetKey2;
  private final String root2;

  private final Dataset dataset;
  private SimpleName taxon;
  private final Dataset dataset2;
  private SimpleName taxon2;
  private final JobResult result;
  private final SqlSessionFactory factory;

  public TaxonomicAlignJob(int userKey, int datasetKey1, String root1, int datasetKey2, String root2, SqlSessionFactory factory) {
    super(userKey);
    this.factory = factory;
    this.datasetKey1 = datasetKey1;
    this.root1 = root1;
    this.datasetKey2 = datasetKey2;
    this.root2 = root2;
    // load dataset & taxon metadata
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var num = session.getMapper(NameUsageMapper.class);
      dataset  = dm.getOrThrow(datasetKey1, Dataset.class);
      dataset2 = dm.getOrThrow(datasetKey2, Dataset.class);
      if (root1 != null) {
        var key = DSID.of(datasetKey1, root1);
        taxon  = num.getSimple(key);
        if (taxon == null) throw NotFoundException.notFound(Taxon.class, key);
      }
      if (root2 != null) {
        var key = DSID.of(datasetKey2, root2);
        taxon2 = num.getSimple(key);
        if (taxon2 == null) throw NotFoundException.notFound(Taxon.class, key);
      }
    }
    this.result = new JobResult(getKey());
  }

  @Override
  public void execute() throws Exception {
    result.calculateSizeAndMd5();
    LOG.info("Aligned all taxa from dataset {} [{}] and {} [{}]. Job {} completed", datasetKey1, taxon, datasetKey2, taxon2, getKey());
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof TaxonomicAlignJob) {
      var oth = (TaxonomicAlignJob) other;
      return datasetKey1 == oth.datasetKey1 &&
              datasetKey2 == oth.datasetKey2 &&
              Objects.equals(root1, oth.root1) &&
              Objects.equals(root2, oth.root2);
    }
    return super.isDuplicate(other);
  }
  @Override
  public String getEmailTemplatePrefix() {
    return "taxalign";
  }

  public JobResult getResult() {
    return result;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public SimpleName getTaxon() {
    return taxon;
  }

  public Dataset getDataset2() {
    return dataset2;
  }

  public SimpleName getTaxon2() {
    return taxon2;
  }
}
