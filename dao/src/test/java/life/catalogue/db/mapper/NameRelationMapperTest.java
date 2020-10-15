package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.NomRelType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

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
    List<NameRelation> outs = nameRelationMapper.list(in.getDatasetKey(), in.getNameId());
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
    assertEquals(0, nameRelationMapper.list(NAME1.getDatasetKey(), NAME1.getId()).size());
    assertEquals(1, nameRelationMapper.list(NAME2.getDatasetKey(), NAME2.getId()).size());
    
    nameRelationMapper.create(newNameRelation());
    nameRelationMapper.create(newNameRelation(NomRelType.BASED_ON));
    nameRelationMapper.create(newNameRelation(NomRelType.CONSERVED));
    commit();
    List<NameRelation> nas = nameRelationMapper.list(NAME1.getDatasetKey(), NAME1.getId());
    
    assertEquals(3, nas.size());
    
    assertEquals(4, nameRelationMapper.list(NAME2.getDatasetKey(), NAME2.getId()).size());
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
