package org.col.db.mapper;

import org.col.api.Name;
import org.col.api.vocab.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NameMapperTest extends MapperTestBase<NameMapper> {

  public NameMapperTest() {
    super(NameMapper.class);
  }

  private Name create() throws Exception {
    Name n = new Name();
    n.setScientificName("Abies alba");
    n.setAuthorship("Mill.");
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setInfraspecificEpithet(null);
    n.setRank(Rank.SPECIES);
    return n;
  }

  @Test
  public void roundtrip() throws Exception {
    Name s1 = create();
    s1.setKey("sk1");
    mapper.insert(s1);

    commit();

    Name s2 = mapper.get(s1.getKey());

    assertEquals(s1, s2);
  }

}