package org.col.api.jackson;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Synonymy;

/**
 *
 */
public class SynonymySerdeTest extends SerdeTestBase<Synonymy> {
  
  public SynonymySerdeTest() {
    super(Synonymy.class);
  }
  
  @Override
  public Synonymy genTestValue() throws Exception {
    return TestEntityGenerator.newSynonymy();
  }
  
}