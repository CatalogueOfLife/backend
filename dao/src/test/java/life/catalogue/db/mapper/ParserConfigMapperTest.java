package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.vocab.Users;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ParserConfigMapperTest extends MapperTestBase<ParserConfigMapper> {

  public ParserConfigMapperTest() {
    super(ParserConfigMapper.class);
  }

  private static ParserConfig create(String name, String author){
    ParserConfig pc = new ParserConfig();
    pc.updateID(name, author);
    pc.setRank(Rank.SPECIES_AGGREGATE);
    pc.setGenus("Genus");
    pc.setSpecificEpithet("species");
    pc.setInfragenericEpithet("subgenus");
    pc.setInfraspecificEpithet("infrasepc");
    pc.setCode(NomCode.BOTANICAL);
    pc.setType(NameType.SCIENTIFIC);
    pc.setTaxonomicNote("sensu lato");
    pc.setNomenclaturalNote("nom. illeg.");
    pc.setCombinationAuthorship(TestEntityGenerator.createAuthorship());
    pc.setBasionymAuthorship(TestEntityGenerator.createAuthorship());

    pc.setCreatedBy(Users.TESTER);
    return pc;
  }

  private static ParserConfig removeDbCreatedProps(ParserConfig pc) {
    NameMapperTest.removeCreatedProps(pc);
    TestEntityGenerator.nullifyDate(pc);
    return pc;
  }

  @Test
  public void roundtrip() throws Exception {
    ParserConfig pc1 = create("Abies", "alba");
    mapper().create(pc1);
    commit();

    ParserConfig pc2 = removeDbCreatedProps(mapper().get(pc1.getId()));
    printDiff(pc1, pc2);
    assertEquals(pc1, pc2);

    mapper().delete(pc1.getId());
    assertNull(mapper().get(pc1.getId()));

    pc1 = create("Abies", "alba");
    mapper().create(pc1);

    pc2 = create("Abies", "berta");
    mapper().create(pc2);
    commit();

    assertEquals(pc1, removeDbCreatedProps(mapper().get(pc1.getId())));
    assertEquals(pc2, removeDbCreatedProps(mapper().get(pc2.getId())));
  }

  @Test
  public void search() throws Exception {
    mapper().create(create("Abies alba", "Mill."));
    mapper().create(create("Polygala vulgaris", "L."));
    mapper().create(create("Sebastes marinus", "(Linnaeus, 1758)"));
    mapper().create(create("Pollachius virens", "(Linnaeus, 1758)"));
    mapper().create(create("Sebastes marinus", null));
    commit();

    assertSearchHits("alba", 1);
    assertSearchHits("dertfvghb", 0);
    assertSearchHits("poll", 1);
    assertSearchHits("la", 2);
    assertSearchHits("1758", 2);
  }

  private void assertSearchHits(String q, int hits){
    assertEquals(hits, mapper().search(new QuerySearchRequest(q), new Page()).size());
  }
}