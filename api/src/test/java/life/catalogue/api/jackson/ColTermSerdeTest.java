package life.catalogue.api.jackson;


import life.catalogue.coldp.ColdpTerm;

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
    return ColdpTerm.Taxon;
  }
  
  @Test
  public void testPrefix() throws Exception {
    String val = ApiModule.MAPPER.writeValueAsString(ColdpTerm.Taxon);
    System.out.println(val);
    Assert.assertTrue(val.contains(":"));
    Assert.assertEquals("\"col:Taxon\"", val);
  
    val = ApiModule.MAPPER.writeValueAsString(ColdpTerm.class_);
    System.out.println(val);
    Assert.assertEquals("\"col:class\"", val);
  }
}
