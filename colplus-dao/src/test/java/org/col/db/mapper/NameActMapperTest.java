package org.col.db.mapper;

import org.col.api.model.Name;
import org.col.api.model.NameAct;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.Origin;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
@SuppressWarnings("static-method")
public class NameActMapperTest extends MapperTestBase<NameActMapper> {

  private NameMapper nameMapper;
  private NameActMapper nameActMapper;

  public NameActMapperTest() {
    super(NameActMapper.class);
  }

  @Before
  public void init() {
    nameMapper = initMybatisRule.getMapper(NameMapper.class);
    nameActMapper = initMybatisRule.getMapper(NameActMapper.class);
  }
  
  @Test
  public void roundtrip() throws Exception {
    NameAct in = newNameAct();
    nameActMapper.create(in);
    assertNotNull(in.getKey());
    commit();
    List<NameAct> outs = nameActMapper.list(in.getNameKey());
    assertEquals(1, outs.size());
    assertEquals(in, outs.get(0));
  }

  @Test
  public void testListByName() throws Exception {
    // NB We have one pre-inserted (apple.sql) NameAct record associated with NAME2 and 3
    assertEquals(0, nameActMapper.list(NAME1.getKey()).size());
    assertEquals(1, nameActMapper.list(NAME2.getKey()).size());

    nameActMapper.create(newNameAct());
    nameActMapper.create(newNameAct(NomActType.BASED_ON));
    nameActMapper.create(newNameAct(NomActType.CONSERVED));
    commit();
    List<NameAct> nas = nameActMapper.list(NAME1.getKey());

    assertEquals(3, nas.size());

    assertEquals(4, nameActMapper.list(NAME2.getKey()).size());
  }

  private static NameAct newNameAct(NomActType type) {
    NameAct na = new NameAct();
    na.setDatasetKey(DATASET1.getKey());
    na.setType(type);
    na.setNameKey(NAME1.getKey());
    na.setRelatedNameKey(NAME2.getKey());
    return na;
  }

  private static NameAct newNameAct() {
    return newNameAct(NomActType.REPLACEMENT_NAME);
  }

}
