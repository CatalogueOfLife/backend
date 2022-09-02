package life.catalogue.assembly;

import life.catalogue.dao.*;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import javax.validation.Validation;
import javax.validation.Validator;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A junit test rule that sets up a new in memory names index and a sync factory.
 * This rule requires that the PgSetupRule was run before!
 */
public class SyncFactoryRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(SyncFactoryRule.class);

  private SyncFactory syncFactory;
  private UsageMatcherGlobal matcher;
  private TaxonDao tdao;
  private SectorDao sdao;
  private NameDao nDao;
  private EstimateDao eDao;
  private DatasetImportDao diDao;
  private SectorImportDao siDao;

  @Override
  protected void before() throws Throwable {
    LOG.info("Setup sync factory");
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    final SqlSessionFactory factory = PgSetupRule.getSqlSessionFactory();
    diDao = new DatasetImportDao(factory, TreeRepoRule.getRepo());
    siDao = new SectorImportDao(factory, TreeRepoRule.getRepo());
    eDao = new EstimateDao(factory, validator);
    nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    sdao = new SectorDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao, validator);
    tdao.setSectorDao(sdao);
    matcher = new UsageMatcherGlobal(NameMatchingRule.getIndex(), PgSetupRule.getSqlSessionFactory());
    syncFactory = new SyncFactory(PgSetupRule.getSqlSessionFactory(), NameMatchingRule.getIndex(), matcher, sdao, siDao, eDao, NameUsageIndexService.passThru());
  }

  public SyncFactory getSyncFactory() {
    return syncFactory;
  }

  public UsageMatcherGlobal getMatcher() {
    return matcher;
  }

  public TaxonDao getTdao() {
    return tdao;
  }

  public SectorDao getSdao() {
    return sdao;
  }

  public NameDao getnDao() {
    return nDao;
  }

  public EstimateDao geteDao() {
    return eDao;
  }

  public DatasetImportDao getDiDao() {
    return diDao;
  }

  public SectorImportDao getSiDao() {
    return siDao;
  }
}
