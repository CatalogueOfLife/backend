package life.catalogue.release;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.importer.PgImportRule;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class ProjectDuplicationIT extends ProjectBaseIT {

  public final static TestDataRule dataRule = TestDataRule.draft();
  public final static PgImportRule importRule = PgImportRule.create(
    NomCode.BOTANICAL,
      DataFormat.ACEF,  1,
      DataFormat.COLDP, 0,
    NomCode.ZOOLOGICAL,
      DataFormat.ACEF,  5, 6
  );

  @ClassRule // this should run AFTER the ProjectBaseIT.classRules
  public final static TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(importRule);

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

    // TODO: setup/clear NamesIndex ???
    SectorSyncIT.syncAll(null);

    ProjectDuplication dupe = projectCopyFactory.buildDuplication(Datasets.COL, Users.TESTER);
    final int datasetKey = dupe.newDatasetKey;
    System.out.println(String.format("Copy dataset %s into %s", Datasets.COL, datasetKey));
    dupe.run();
    assertEquals(ImportState.FINISHED, dupe.getMetrics().getState());
  }
}