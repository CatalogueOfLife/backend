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
  public Reference genTestValue() throws Exception {
    return TestEntityGenerator.newReference();
  }

  @Override
  protected void debug(Wrapper<Reference> wrapper, Wrapper<Reference> wrapper2){
    System.out.println("1: " + wrapper.value.getCsl());
    System.out.println("2: " + wrapper2.value.getCsl());
  }
}