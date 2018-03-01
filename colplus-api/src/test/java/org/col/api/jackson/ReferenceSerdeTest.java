package org.col.api.jackson;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Reference;

/**
 *
 */
public class ReferenceSerdeTest extends SerdeTestBase<Reference> {

  public ReferenceSerdeTest() {
    super(Reference.class);
  }

  @Override
  Reference genTestValue() throws Exception {
    return TestEntityGenerator.newReference();
  }

}