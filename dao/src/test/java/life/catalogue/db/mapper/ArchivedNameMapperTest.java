package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static life.catalogue.api.vocab.IdReportType.CREATED;
import static org.junit.Assert.*;

public class ArchivedNameMapperTest extends MapperTestBase<ArchivedNameMapper> {

  public ArchivedNameMapperTest() {
    super(ArchivedNameMapper.class);
  }

  @Test
  public void build() throws Exception {
    ArchivedNameUsage u = mapper().build(DSID.of(appleKey, "root-1"));
    assertNotNull(u);
    assertNotNull(u.getName());
  }

  @Test
  public void roundtrip() throws Exception {
    ArchivedNameUsage u = create();
    // test create
    mapper().create(u);
    commit();

    // test get
    var dsid = u.getKey();
    System.out.println(dsid.getDatasetKey());
    System.out.println(dsid.getId());
    var obj = mapper().get(dsid);
    assertEquals(u, obj);

    // test processDataset
    mapper().processDataset(999).forEach(o -> fail("should never reach here"));
    mapper().processDataset(appleKey).forEach(o -> assertEquals(u, o));
  }


  public static ArchivedNameUsage create() {
    Name n = TestEntityGenerator.newName(appleKey);
    Taxon t = TestEntityGenerator.newTaxon(n);
    ArchivedNameUsage u = new ArchivedNameUsage(t);
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