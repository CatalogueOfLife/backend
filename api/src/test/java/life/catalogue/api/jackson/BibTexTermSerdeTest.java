package life.catalogue.api.jackson;

import life.catalogue.api.datapackage.ColdpTerm;
import org.gbif.dwc.terms.BibTexTerm;
import org.gbif.dwc.terms.Term;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class BibTexTermSerdeTest extends SerdeTestBase<Term> {

  public BibTexTermSerdeTest() {
    super(Term.class);
  }
  
  @Override
  public Term genTestValue() throws Exception {
    return BibTexTerm.CLASS_TERM;
  }
  
  @Test
  public void testPrefix() throws Exception {
    String val = ApiModule.MAPPER.writeValueAsString(BibTexTerm.CLASS_TERM);
    System.out.println(val);
    Assert.assertTrue(val.contains(":"));
    Assert.assertEquals("\"bib:BibTeX\"", val);
  
    val = ApiModule.MAPPER.writeValueAsString(new BibTexTerm("creator"));
    System.out.println(val);
    Assert.assertEquals("\"bib:creator\"", val);
  }
}
