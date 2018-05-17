package org.col.db.mapper;

import java.util.Random;

import org.apache.ibatis.annotations.Param;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.model.TermRecord;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class VerbatimRecordMapperTest extends MapperTestBase<VerbatimRecordMapper> {
  Random rnd = new Random();

  public VerbatimRecordMapperTest() {
    super(VerbatimRecordMapper.class);
  }

  @Test
  public void roundtrip() {
    TermRecord r1 = TestEntityGenerator.createVerbatim();
    mapper().create(r1);

    commit();

    TermRecord r2 = mapper().get(r1.getKey());

    assertEquals(r1, r2);
  }

  @Test
  public void getByEntity() {
    TermRecord r1 = TestEntityGenerator.createVerbatim();
    mapper().create(r1);

    // link to name
    Name n = TestEntityGenerator.NAME1;
    n.setKey(null);
    n.setId("cbhdsgv6e");
    n.setVerbatimKey(r1.getKey());

    mapper(NameMapper.class).create(n);
    commit();

    TermRecord r2 = mapper().getByEntity(Name.class, n.getKey());

    assertEquals(r1, r2);
  }

}