package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.importer.PgImportRule;
import life.catalogue.matching.NameIndexFactory;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;

public class ProjectDuplicationIT {

  public final static PgSetupRule pg = new PgSetupRule();
  public final static TestDataRule dataRule = TestDataRule.draft();
  public final static PgImportRule importRule = PgImportRule.create(
    NomCode.BOTANICAL,
    DataFormat.ACEF,  1,
    DataFormat.COLDP, 0,
    NomCode.ZOOLOGICAL,
    DataFormat.ACEF,  5, 6
  );
  public final static TreeRepoRule treeRepoRule = new TreeRepoRule();

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(treeRepoRule)
    .around(importRule);

  DatasetImportDao diDao;
  SectorImportDao siDao;
  TaxonDao tdao;

  @Before
  public void init () throws IOException, SQLException {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    siDao = new SectorImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    NameDao nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru());
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru());
    // reset draft
    dataRule.truncateDraft();
    dataRule.loadData(true);
  }

  int datasetKey(int key, DataFormat format) {
    return importRule.datasetKey(key, format);
  }

  @Test
  public void duplicate() {
    // prepare a sync
    NameUsageBase src = SectorSyncIT.getByName(datasetKey(1, DataFormat.ACEF), Rank.ORDER, "Fabales");
    NameUsageBase trg = SectorSyncIT.getByName(Datasets.DRAFT_COL, Rank.PHYLUM, "Tracheophyta");
    DSID<Integer> s1 = SectorSyncIT.createSector(Sector.Mode.ATTACH, src, trg);

    src = SectorSyncIT.getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    trg = SectorSyncIT.getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    DSID<Integer> s2 = SectorSyncIT.createSector(Sector.Mode.UNION, src, trg);

    src = SectorSyncIT.getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = SectorSyncIT.getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    DSID<Integer> s3 = SectorSyncIT.createSector(Sector.Mode.ATTACH, src, trg);

    SectorSyncIT.syncAll(siDao);

    // test ProjectDuplication
    Dataset d;
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      d = dm.get(Datasets.DRAFT_COL);
      d.setTitle(d.getTitle() + " copy");
      d.setKey(1100);
      d.setAlias(d.getAlias() + " copy");
      dm.createWithKey(d);
      session.commit();
    }

    ProjectDuplication dupe = new ProjectDuplication(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), diDao, Datasets.DRAFT_COL, d, Users.TESTER);
    dupe.run();
    assertEquals(ImportState.FINISHED, dupe.getMetrics().getState());
  }
}