package life.catalogue.release;

import com.google.common.eventbus.EventBus;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
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
import static org.mockito.Mockito.mock;

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
  DatasetDao dDao;
  TaxonDao tdao;
  ReleaseManager releaseManager;

  @Before
  public void init () throws IOException, SQLException {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    EventBus bus = mock(EventBus.class);
    ExportManager exm = mock(ExportManager.class);
    DatasetExportDao exDao = mock(DatasetExportDao.class);
    DoiService doiService = mock(DoiService.class);
    dDao = new DatasetDao(PgSetupRule.getSqlSessionFactory(), null, ImageService.passThru(), diDao, exDao, NameUsageIndexService.passThru(), null, bus);
    siDao = new SectorImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    NameDao nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru());
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru());
    releaseManager = new ReleaseManager(null, diDao, dDao, exm, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, PgSetupRule.getSqlSessionFactory(), new WsServerConfig());
  }

  int datasetKey(int key, DataFormat format) {
    return importRule.datasetKey(key, format);
  }

  @Test
  public void duplicate() {
    // prepare a sync
    NameUsageBase src = SectorSyncIT.getByName(datasetKey(1, DataFormat.ACEF), Rank.ORDER, "Fabales");
    NameUsageBase trg = SectorSyncIT.getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    SectorSyncIT.createSector(Sector.Mode.ATTACH, src, trg);

    src = SectorSyncIT.getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    trg = SectorSyncIT.getByName(Datasets.COL, Rank.CLASS, "Insecta");
    SectorSyncIT.createSector(Sector.Mode.UNION, src, trg);

    src = SectorSyncIT.getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = SectorSyncIT.getByName(Datasets.COL, Rank.CLASS, "Insecta");
    SectorSyncIT.createSector(Sector.Mode.ATTACH, src, trg);

    SectorSyncIT.setupNamesIndex(PgSetupRule.getSqlSessionFactory());
    SectorSyncIT.syncAll(siDao);

    ProjectDuplication dupe = releaseManager.buildDuplication(Datasets.COL, Users.TESTER);
    final int datasetKey = dupe.newDatasetKey;
    System.out.println(String.format("Copy dataset %s into %s", Datasets.COL, datasetKey));
    dupe.run();
    assertEquals(ImportState.FINISHED, dupe.getMetrics().getState());
  }
}