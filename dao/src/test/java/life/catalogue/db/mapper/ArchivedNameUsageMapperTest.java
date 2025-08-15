package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Gender;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArchivedNameUsageMapperTest extends MapperTestBase<ArchivedNameUsageMapper> {

  public ArchivedNameUsageMapperTest() {
    super(ArchivedNameUsageMapper.class);
  }

  @Test
  public void get() throws Exception {
    // test get SQL
    var obj = mapper().get(DSID.of(appleKey, "xxx"));
    assertNull(obj);
  }

  @Test
  public void nidxProcessing() throws Exception {
    mapper().processDataset(999).forEach(o -> fail("should never reach here"));
    mapper().processDataset(appleKey).forEach(o -> assertNotNull(o));
  }

  @Test
  public void processArchivedUsages() throws Exception {
    mapper().processArchivedUsages(999).forEach(o -> fail("should never reach here"));
    mapper().processArchivedUsages(appleKey).forEach(o -> {
      assertNotNull(o);
      assertTrue(o.getReleaseKeys().length>0);
    });
  }

  @Test
  public void indexGroupIds() throws Exception {
    var res = mapper().indexGroupIds(1);
    assertEquals(0, res.size());
  }

  @Test
  public void createMissingUsages() throws Exception {
    mapper().createMissingUsages(Datasets.COL, 1000);
  }

  public static ArchivedNameUsage create() {
    Name n = TestEntityGenerator.newName(appleKey);
    n.setNamesIndexType(null);
    n.addIdentifier("tsn:1234");
    n.setOriginalSpelling(true);
    n.setGenderAgreement(false);
    n.setGender(Gender.NEUTER);
    n.setEtymology("concolor is Latin for of uniform color");
    Taxon t = TestEntityGenerator.newTaxon(n);
    t.addIdentifier("col:DF2R");
    t.addIdentifier("gbif:456789");
    ArchivedNameUsage u = getArchivedNameUsage(t);
    // clear unsupported fields
    TestEntityGenerator.setUserDate(u, null, null);
    TestEntityGenerator.setUserDate(u.getName(), null, null);
    return u;
  }

  @NotNull
  private static ArchivedNameUsage getArchivedNameUsage(Taxon t) {
    ArchivedNameUsage u = new ArchivedNameUsage(t);
    u.setDatasetKey(3); // belongs to project
    u.setReleaseKeys(new int[]{12});
    u.setExtinct(true);
    u.setClassification(List.of(
      new SimpleName("a", "Aster spicata", "Döring", Rank.SPECIES),
      new SimpleName("a", "Asteraceae", "Miller", Rank.FAMILY),
      new SimpleName("a2", "Asterales", Rank.ORDER),
      new SimpleName("m", "Magnifica", Rank.CLASS)
    ));
    u.setPublishedIn("published in sth");
    u.setStatus(TaxonomicStatus.SYNONYM);
    return u;
  }

}