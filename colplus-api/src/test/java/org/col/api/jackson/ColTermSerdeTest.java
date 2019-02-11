package org.col.api.jackson;

import org.col.api.datapackage.ColTerm;
import org.gbif.dwc.terms.Term;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ColTermSerdeTest extends SerdeTestBase<Term> {
  
  public ColTermSerdeTest() {
    super(Term.class);
  }
  
  @Override
  public Term genTestValue() throws Exception {
    return ColTerm.Taxon;
  }
  
  @Test
  public void testPrefix() throws Exception {
    String val = ApiModule.MAPPER.writeValueAsString(ColTerm.Taxon);
    System.out.println(val);
    Assert.assertTrue(val.contains(":"));
    Assert.assertEquals("\"col:Taxon\"", val);
  }
}
