package org.col.db.mapper;

import java.util.List;

import org.col.api.model.NameRelation;
import org.col.api.vocab.NomRelType;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
@SuppressWarnings("static-method")
public class NameRelationMapperTest extends MapperTestBase<NameRelationMapper> {

  private NameMapper nameMapper;
  private NameRelationMapper nameRelationMapper;

  public NameRelationMapperTest() {
    super(NameRelationMapper.class);
  }

  @Before
  public void init() {
    nameMapper = initMybatisRule.getMapper(NameMapper.class);
    nameRelationMapper = initMybatisRule.getMapper(NameRelationMapper.class);
  }
  
  @Test
  public void roundtrip() throws Exception {
    NameRelation in = newNameAct();
    nameRelationMapper.create(in);
    assertNotNull(in.getKey());
    commit();
    List<NameRelation> outs = nameRelationMapper.list(in.getDatasetKey(), in.getNameKey());
    assertEquals(1, outs.size());
    assertEquals(in, outs.get(0));
  }

  @Test
  public void testListByName() throws Exception {
    // NB We have one pre-inserted (apple.sql) NameAct record associated with NAME2 and 3
    assertEquals(0, nameRelationMapper.list(NAME1.getDatasetKey(), NAME1.getKey()).size());
    assertEquals(1, nameRelationMapper.list(NAME2.getDatasetKey(), NAME2.getKey()).size());

    nameRelationMapper.create(newNameAct());
    nameRelationMapper.create(newNameAct(NomRelType.BASED_ON));
    nameRelationMapper.create(newNameAct(NomRelType.CONSERVED));
    commit();
    List<NameRelation> nas = nameRelationMapper.list(NAME1.getDatasetKey(), NAME1.getKey());

    assertEquals(3, nas.size());

    assertEquals(4, nameRelationMapper.list(NAME2.getDatasetKey(), NAME2.getKey()).size());
  }

  private static NameRelation newNameAct(NomRelType type) {
    NameRelation na = new NameRelation();
    na.setDatasetKey(DATASET11.getKey());
    na.setType(type);
    na.setNameKey(NAME1.getKey());
    na.setRelatedNameKey(NAME2.getKey());
    return na;
  }

  private static NameRelation newNameAct() {
    return newNameAct(NomRelType.REPLACEMENT_NAME);
  }

}
