package life.catalogue.api.jackson;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.junit.Test;

/**
 *
 */
public class UnknownTermSerdeTest extends SerdeTestBase<Term> {
  
  public UnknownTermSerdeTest() {
    super(Term.class);
  }
  
  @Override
  public UnknownTerm genTestValue() throws Exception {
    return UnknownTerm.build("http://col.plus/terms/punk");
  }

  @Test
  public void testUnkown() throws Exception {
    testRoundtrip(UnknownTerm.build("Col_name"));
  }


}
