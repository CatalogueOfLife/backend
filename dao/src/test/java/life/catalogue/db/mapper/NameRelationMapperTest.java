package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.Users;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
@SuppressWarnings("static-method")
public class NameRelationMapperTest extends MapperTestBase<NameRelationMapper> {
  
  private NameRelationMapper nameRelationMapper;
  
  public NameRelationMapperTest() {
    super(NameRelationMapper.class);
  }
  
  @Before
  public void init() {
    nameRelationMapper = testDataRule.getMapper(NameRelationMapper.class);
  }
  
  @Test
  public void roundtrip() throws Exception {
    NameRelation in = nullifyDate(newNameRelation());
    nameRelationMapper.create(in);
    assertNotNull(in.getKey());
    commit();
    List<NameRelation> outs = nameRelationMapper.listByName(in.getNameKey());
    assertEquals(1, outs.size());
    assertEquals(in, nullifyDate(outs.get(0)));
  }

  @Test
  public void sectorProcessable() throws Exception {
    SectorProcessableTestComponent.test(mapper(), DSID.of(Datasets.COL, 1));
  }

  @Test
  public void testListByName() throws Exception {
    // NB We have one pre-inserted (apple.sql) NameAct record associated with NAME2 and 3
    assertEquals(0, nameRelationMapper.listByName(NAME1).size());
    assertEquals(1, nameRelationMapper.listByName(NAME2).size());

    nameRelationMapper.create(newNameRelation());
    nameRelationMapper.create(newNameRelation(NomRelType.BASED_ON));
    nameRelationMapper.create(newNameRelation(NomRelType.CONSERVED));
    commit();
    List<NameRelation> nas = nameRelationMapper.listByName(NAME1);

    assertEquals(3, nas.size());

    assertEquals(1, nameRelationMapper.listByName(NAME2).size());
    assertEquals(3, nameRelationMapper.listByRelatedName(NAME2).size());
  }

  @Test
  public void testGraph() throws Exception {
    NameMapper nm = mapper(NameMapper.class);
    NameRelationMapper nrm = mapper(NameRelationMapper.class);
    // n1-n4 are homotypic names via transitive relations, n5+n6 are homotypic and n7 is not homotypic
    Name n = null;
    for (int i = 1; i<=7; i++) {
      n = TestEntityGenerator.newName("n"+i);
      n.applyUser(Users.IMPORTER);
      nm.create(n);
    }
    NameRelation rel = new NameRelation();
    rel.applyUser(Users.IMPORTER);
    rel.setDatasetKey(n.getDatasetKey());

    rel.setNameId("n1");
    rel.setRelatedNameId("n2");
    rel.setType(NomRelType.HOMOTYPIC);
    nrm.create(rel);

    rel.setType(NomRelType.BASIONYM);
    nrm.create(rel);

    rel.setNameId("n1");
    rel.setRelatedNameId("n3");
    rel.setType(NomRelType.BASED_ON);
    nrm.create(rel);

    rel.setNameId("n3");
    rel.setRelatedNameId("n4");
    rel.setType(NomRelType.HOMOTYPIC);
    nrm.create(rel);

    rel.setNameId("n4");
    rel.setRelatedNameId("n3");
    rel.setType(NomRelType.REPLACEMENT_NAME);
    nrm.create(rel);

    rel.setNameId("n4");
    rel.setRelatedNameId("n3");
    rel.setType(NomRelType.SPELLING_CORRECTION);
    nrm.create(rel);

    // group 5+6
    rel.setNameId("n5");
    rel.setRelatedNameId("n6");
    rel.setType(NomRelType.SPELLING_CORRECTION);
    nrm.create(rel);

    rel.setNameId("n6");
    rel.setRelatedNameId("n5");
    rel.setType(NomRelType.CONSERVED);
    nrm.create(rel);

    // group 7
    rel.setNameId("n7");
    rel.setRelatedNameId("n2");
    rel.setType(NomRelType.LATER_HOMONYM);
    nrm.create(rel);

    commit();

    // NB We have one pre-inserted (apple.sql) NameAct record associated with NAME2 and 3
    var h1 = Set.of("n1", "n2", "n3", "n4");
    assertEquals(h1, Set.copyOf(mapper().listRelatedNameIDs(DSID.of(n.getDatasetKey(), "n1"), NomRelType.HOMOTYPIC_RELATIONS)));
    assertEquals(h1, Set.copyOf(mapper().listRelatedNameIDs(DSID.of(n.getDatasetKey(), "n2"), NomRelType.HOMOTYPIC_RELATIONS)));
    assertEquals(Set.of("n5", "n6"), Set.copyOf(mapper().listRelatedNameIDs(DSID.of(n.getDatasetKey(), "n5"), NomRelType.HOMOTYPIC_RELATIONS)));
    assertEquals(Collections.emptySet(), Set.copyOf(mapper().listRelatedNameIDs(DSID.of(n.getDatasetKey(), "n7"), NomRelType.HOMOTYPIC_RELATIONS)));
  }

  private static NameRelation newNameRelation(NomRelType type) {
    NameRelation na = TestEntityGenerator.setUserDate(new NameRelation());
    na.setDatasetKey(DATASET11.getKey());
    na.setType(type);
    na.setNameId(NAME1.getId());
    na.setRelatedNameId(NAME2.getId());
    return na;
  }
  
  private static NameRelation newNameRelation() {
    return newNameRelation(NomRelType.REPLACEMENT_NAME);
  }
  
}
