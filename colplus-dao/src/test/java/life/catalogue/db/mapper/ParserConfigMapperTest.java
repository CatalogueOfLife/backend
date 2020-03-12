package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.ParserConfig;
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
    pc.setRemarks("nom. illeg.");
    pc.setCombinationAuthorship(TestEntityGenerator.createAuthorship());
    pc.setBasionymAuthorship(TestEntityGenerator.createAuthorship());

    pc.setCreatedBy(Users.TESTER);
    return pc;
  }

  private static ParserConfig removeDbCreatedProps(ParserConfig pc) {
    TestEntityGenerator.nullifyDate(pc);
    return pc;
  }

  @Test
  public void roundtrip() throws Exception {
    ParserConfig pc1 = create("Abies", "alba");
    mapper().create(pc1);
    commit();

    ParserConfig pc2 = removeDbCreatedProps(mapper().get(pc1.getId()));
    //printDiff(u1, u2);
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
}