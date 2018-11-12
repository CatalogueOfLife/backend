package org.col.api.jackson;

import org.col.api.TestEntityGenerator;
import org.col.api.model.VerbatimRecord;

/**
 *
 */
public class VerbatimRecordSerdeTest extends SerdeTestBase<VerbatimRecord> {
  
  public VerbatimRecordSerdeTest() {
    super(VerbatimRecord.class);
  }
  
  @Override
  protected void debug(String json, Wrapper<VerbatimRecord> wrapper, Wrapper<VerbatimRecord> wrapper2) {
    //System.out.println(json);
  }
  
  @Override
  public VerbatimRecord genTestValue() throws Exception {
    return TestEntityGenerator.createVerbatim();
  }
}