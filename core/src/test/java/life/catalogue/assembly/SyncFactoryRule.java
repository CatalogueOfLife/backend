package life.catalogue.assembly;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.dao.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TreeRepoRule;
import life.catalogue.matching.MatchingService;
import life.catalogue.matching.MatchingStorage;
import life.catalogue.matching.nidx.NameIndexFactory;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * A junit test rule that sets up a new in memory names index and a sync factory.
 * This rule requires that the PgSetupRule and TreeRepoRule was run before!
 */
public class SyncFactoryRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(SyncFactoryRule.class);

  private static SyncFactory syncFactory;
  private SqlSession matchingSession;
  private MatchingService<SimpleNameCached> matcher;
  private TaxonDao tdao;
  private SectorDao sdao;
  private NameDao nDao;
  private EstimateDao eDao;
  private DatasetImportDao diDao;
  private SectorImportDao siDao;
  private MatchingStorage<SimpleNameCached> mstore;

  @Override
  protected void before() throws Throwable {
    LOG.info("Setup sync factory");
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    final SqlSessionFactory factory = SqlSessionFactoryRule.getSqlSessionFactory();
    diDao = new DatasetImportDao(factory, TreeRepoRule.getRepo());
    siDao = new SectorImportDao(factory, TreeRepoRule.getRepo());
    eDao = new EstimateDao(factory, validator);
    nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    sdao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao, validator);
    tdao.setSectorDao(sdao);
    syncFactory = new SyncFactory(SqlSessionFactoryRule.getSqlSessionFactory(), NameMatchingRule.getIndex(), sdao, siDao, eDao, NameUsageIndexService.passThru(), new EventBus("test-bus"));
  }

  public static SyncFactory getFactory() {
    return syncFactory;
  }

  public MatchingService<SimpleNameCached> getMatcher() {
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
