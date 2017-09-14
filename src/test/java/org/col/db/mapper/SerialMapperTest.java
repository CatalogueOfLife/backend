package org.col.db.mapper;

import org.col.api.Serial;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class SerialMapperTest extends MapperTestBase<SerialMapper> {

  public SerialMapperTest() {
    super(SerialMapper.class);
  }

  @Test
  public void writeRead() throws Exception {
    Serial s1 = new Serial();
    mapper.insert(s1);

    commit();

    Serial s2 = mapper.get(s1.getKey());

    assertEquals(s1, s2);
  }

}