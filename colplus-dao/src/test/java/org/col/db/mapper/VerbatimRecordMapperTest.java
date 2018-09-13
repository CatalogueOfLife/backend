package org.col.db.mapper;

import java.util.Random;

import org.col.api.TestEntityGenerator;
import org.col.api.model.VerbatimRecord;
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
    VerbatimRecord r1 = TestEntityGenerator.createVerbatim();
    mapper().create(r1);

    commit();

    VerbatimRecord r2 = mapper().get(r1.getDatasetKey(), r1.getKey());

    assertEquals(r1, r2);
  }

}