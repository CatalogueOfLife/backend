package life.catalogue.assembly;

import life.catalogue.TestDataRules;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.db.mapper.*;
import life.catalogue.junit.*;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * A source specimen typifying several names is copied once per sync and linked to the copied names only.
 * https://github.com/CatalogueOfLife/backend/issues/1645
 */
public class SectorSyncTypeMaterialIT extends SectorSyncTestBase {

  final static SqlSessionFactoryRule pg = new PgSetupRule();
  final static TestDataRule dataRule = TestDataRules.syncs();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
      .outerRule(pg)
      .around(dataRule)
      .around(treeRepoRule)
      .around(matchingRule)
      .around(syncFactoryRule);

  TestDataRule draftRule;

  @Before
  public void init () throws IOException, SQLException {
    draftRule = TestDataRule.draft();
    draftRule.initSession();
    draftRule.truncateDraft();
    draftRule.loadData();
    matchingRule.rematch(draftRule.testData.key);
  }

  @After
  public void after () throws IOException, SQLException {
    draftRule.getSqlSession().close();
  }

  @Test
  public void multiNameSpecimen() throws Exception {
    final int srcKey = dataRule.mapKey(DataFormat.COLDP, 0);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var tmm = session.getMapper(TypeMaterialMapper.class);
      // the holotype of the subspecies also typifies the species
      tmm.createName(srcKey, "GH00006265", new TypifiedName("1001", TypeStatus.ISOTYPE));
      // the subspecies isotype also typifies Gundelia tournefortii, outside of the Crepis sector
      tmm.createName(srcKey, "MIN1000737", new TypifiedName("1011", TypeStatus.PARATYPE));
    }

    NameUsageBase crepis = getByName(srcKey, Rank.GENUS, "Crepis");
    NameUsageBase tracheophyta = getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    var sectorKey = createSector(Sector.Mode.ATTACH, crepis, tracheophyta);
    syncAll();

    var bakeri = getByName(Datasets.COL, Rank.SPECIES, "Crepis bakeri");
    var idahoensis = getByName(Datasets.COL, Rank.SUBSPECIES, "Crepis bakeri subsp. idahoensis");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var tmm = session.getMapper(TypeMaterialMapper.class);

      var bakeriTypes = tmm.listDesignations(bakeri.getName());
      assertEquals(2, bakeriTypes.size());
      var ghBakeri = byCitation(bakeriTypes, "GH 00006265");
      assertEquals(TypeStatus.ISOTYPE, ghBakeri.getStatus());
      assertEquals(TypeStatus.HOLOTYPE, byCitation(bakeriTypes, "NDG 65570").getStatus());

      var idahoensisTypes = tmm.listDesignations(idahoensis.getName());
      assertEquals(2, idahoensisTypes.size());
      var ghIdahoensis = byCitation(idahoensisTypes, "GH 00006265");
      assertEquals(TypeStatus.HOLOTYPE, ghIdahoensis.getStatus());

      // one copy of the specimen for both names
      var gh = ghBakeri.getTypeMaterial();
      assertEquals(gh.getId(), ghIdahoensis.getTypeMaterial().getId());
      assertEquals(2, gh.getNames().size());
      assertEquals(sectorKey.getId(), gh.getSectorKey());

      // the name outside the sector was not copied, so it is not linked
      var min = byCitation(idahoensisTypes, "MIN 1000737").getTypeMaterial();
      assertEquals(List.of(new TypifiedName(idahoensis.getName().getId(), TypeStatus.ISOTYPE)), min.getNames());

      // 3 specimens with 4 links in the project
      assertEquals(3, (int) session.getMapper(SectorImportMapper.class).countTypeMaterial(Datasets.COL, sectorKey.getId()));
    }
  }

  private static TypeDesignation byCitation(List<TypeDesignation> designations, String citationPart) {
    return designations.stream()
      .filter(d -> d.getTypeMaterial().getCitation().contains(citationPart))
      .findFirst()
      .orElseThrow(() -> new AssertionError("No type material with citation " + citationPart));
  }
}
