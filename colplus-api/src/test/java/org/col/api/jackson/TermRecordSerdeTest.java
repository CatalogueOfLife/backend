package org.col.api.jackson;

import java.util.Random;

import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.*;
import org.junit.Ignore;

/**
 *
 */
public class TermRecordSerdeTest extends SerdeTestBase<TermRecord> {

  public TermRecordSerdeTest() {
    super(TermRecord.class);
  }

  @Override
  protected void debug(String json, Wrapper<TermRecord> wrapper, Wrapper<TermRecord> wrapper2) {
    //System.out.println(json);
  }

  @Override
  public TermRecord genTestValue() throws Exception {
    return TestEntityGenerator.createVerbatim();
  }
}