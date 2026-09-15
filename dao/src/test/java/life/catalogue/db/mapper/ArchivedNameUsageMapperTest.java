package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Gender;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import jakarta.validation.constraints.NotNull;
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
  public void archiveStatements() throws Exception {
    // apple has no release data, but this proves every statement runs against the schema
    final int rel = 1000;
    assertEquals(0, mapper().createMissingUsages(Datasets.COL, rel));
    assertEquals(0, mapper().updateExistingUsages(Datasets.COL, rel, List.of()));
    assertEquals(0, mapper().updateExistingUsages(Datasets.COL, rel, List.of(1, 2)));
    assertEquals(0, mapper().addReleaseKey(Datasets.COL, rel));
    assertEquals(0, mapper().tidyReleaseKeys(Datasets.COL));
    assertEquals(0, mapper().countMissingUsages(Datasets.COL, rel));
    assertEquals(0, mapper().countOutdatedUsages(Datasets.COL, rel, List.of(1), false));
    assertEquals(0, mapper().countOutdatedUsages(Datasets.COL, rel, List.of(), true));
    assertEquals(0, mapper().countMissingReleaseKeys(Datasets.COL, rel));
    assertFalse(mapper().isReleaseArchived(Datasets.COL, rel));
    assertFalse(mapper().hasUsages(rel));
    assertEquals(0, mapper().clearSuperseded(Datasets.COL, rel));
  }

  public static ArchivedNameUsage create() {
    Name n = TestEntityGenerator.newName(appleKey);
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