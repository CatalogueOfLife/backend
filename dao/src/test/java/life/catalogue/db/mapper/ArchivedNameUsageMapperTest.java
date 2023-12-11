package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Gender;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ArchivedNameUsageMapperTest extends MapperTestBase<ArchivedNameUsageMapper> {

  private ArchivedNameUsage orig;
  IndexName nidx;

  public ArchivedNameUsageMapperTest() {
    super(ArchivedNameUsageMapper.class);
  }

  @Before
  public void init() throws Exception {
    orig = create();
    mapper().create(orig);
    // add names index match
    var nim = mapper(NamesIndexMapper.class);
    nidx = new IndexName(orig.getName());
    nim.create(nidx);

    var nmm = mapper(ArchivedNameUsageMatchMapper.class);
    nmm.create(DSID.of(3, orig.getId()), nidx.getKey(), MatchType.EXACT);
    commit();
  }

  @Test
  public void roundtrip() throws Exception {
    // test get
    var dsid = orig.getKey();
    System.out.println(dsid.getDatasetKey());
    System.out.println(dsid.getId());
    var obj = mapper().get(dsid);
    assertEquals(orig, obj);

    // test processDataset
    mapper().processDataset(999).forEach(o -> fail("should never reach here"));
    mapper().processDataset(appleKey).forEach(o -> assertEquals(orig, o));
  }

  @Test
  public void nidxProcessing() throws Exception {
    mapper().processArchivedNames(999, false).forEach(o -> fail("should never reach here"));
    mapper().processArchivedNames(3, false).forEach(n -> {
      assertEquals(orig.getId(), n.getId()); // we expose usage ids only and use those in the archived match table too
      assertEquals(nidx.getKey(), n.getNamesIndexId());
      assertEquals(MatchType.EXACT, n.getNamesIndexType());
      assertEquals(orig.getName().getRank(), n.getRank());
      assertEquals(orig.getName().getCode(), n.getCode());
      assertEquals(orig.getName().getScientificName(), n.getScientificName());
      assertEquals(orig.getName().getAuthorship(), n.getAuthorship());
      assertEquals(orig.getName().getRemarks(), n.getRemarks());
      assertEquals(orig.getName().isOriginalSpelling(), n.isOriginalSpelling());
    });

    mapper().processArchivedUsages(999).forEach(o -> fail("should never reach here"));
    mapper().processArchivedUsages(3).forEach(u -> {
      assertEquals(orig.getId(), u.getId());
      assertEquals(nidx.getKey(), u.getNamesIndexId());
      assertEquals(MatchType.EXACT, u.getNamesIndexMatchType());
      assertEquals((int)orig.getLastReleaseKey(), u.getLastReleaseKey());
      assertEquals(orig.getRank(), u.getRank());
      assertEquals(orig.getName().getScientificName(), u.getName());
      assertEquals(orig.getName().getAuthorship(), u.getAuthorship());
      assertEquals(orig.getParent().getName(), u.getParent());
    });
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
    ArchivedNameUsage u = new ArchivedNameUsage(t);
    u.setDatasetKey(3); // belongs to project
    u.setLastReleaseKey(12);
    u.setExtinct(true);
    u.setClassification(List.of(
      new SimpleName("a", "Aster spicata", "Döring", Rank.SPECIES),
      new SimpleName("a", "Asteraceae", "Miller", Rank.FAMILY),
      new SimpleName("a2", "Asterales", Rank.ORDER),
      new SimpleName("m", "Magnifica", Rank.CLASS)
    ));
    u.setPublishedIn("published in sth");
    u.setStatus(TaxonomicStatus.SYNONYM);
    // clear unsupported fields
    TestEntityGenerator.setUserDate(u, null, null);
    TestEntityGenerator.setUserDate(u.getName(), null, null);
    return u;
  }

}