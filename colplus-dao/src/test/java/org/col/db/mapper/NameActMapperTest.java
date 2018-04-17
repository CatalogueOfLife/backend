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
    NameAct out = nameActMapper.get(in.getKey());
    assertTrue(in.equals(out));
  }

  @Test
  public void testListByName() throws Exception {
    nameActMapper.create(newNameAct());
    nameActMapper.create(newNameAct(NomActType.BASED_ON));
    nameActMapper.create(newNameAct(NomActType.CONSERVED));
    commit();
    List<NameAct> nas = nameActMapper.listByName(NAME1.getKey());
    /*
     * NB We have one pre-inserted (apple.sql) NameAct record associated with NAME1; one of the
     * records inserted here is _not_ associated with NAME1, so we should have 3 NameAct records
     * associated with NAME1
     */
    assertEquals(3, nas.size());
  }

  private Name createName(String id, String name, Name homotypicGroup) {
    Name n = new Name();
    n.setDatasetKey(DATASET1.getKey());
    n.setId(id);
    n.setScientificName(name);
    n.setType(NameType.SCIENTIFIC);
    n.setRank(Rank.UNRANKED);
    n.setOrigin(Origin.SOURCE);
    if (homotypicGroup != null) {
      n.setHomotypicNameKey(homotypicGroup.getKey());
    }
    nameMapper.create(n);
    return n;
  }

  private NameAct createAct(int nameKey, int relatedNameKey, NomActType type) {
    NameAct act = new NameAct();
    act.setDatasetKey(DATASET1.getKey());
    act.setNameKey(nameKey);
    act.setRelatedNameKey(relatedNameKey);
    act.setType(type);

    nameActMapper.create(act);
    return act;
  }

  @Test
  public void testListByHomotypicGroup() throws Exception {

    NameMapper nameMapper = initMybatisRule.getMapper(NameMapper.class);

    // Create basionym
    Name basionym = createName("foo-bar", "Foo bar", null);

    // Create recombination referencing basionym
    Name name = createName("foo-new", "Too bar", basionym);
    createAct(name.getKey(), basionym.getKey(), NomActType.BASIONYM);

    // Create another name referencing basionym
    Name kuhbar = createName("foo-too", "Kuh bar", basionym);

    createAct(kuhbar.getKey(), basionym.getKey(), NomActType.BASIONYM);

    commit();

    // So total size of homotypic group is 3
    Integer nameKey = nameMapper.lookupKey("foo-bar", DATASET1.getKey());
    List<NameAct> nas = nameActMapper.listByHomotypicGroup(nameKey);
    assertEquals(3, nas.size());

    
    // now add a new convered name
    name = createName("foo-too2", "Kuh bahr", basionym);

    createAct(name.getKey(), kuhbar.getKey(), NomActType.CONSERVED);
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
