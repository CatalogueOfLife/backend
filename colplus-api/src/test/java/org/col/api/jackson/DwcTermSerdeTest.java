package org.col.api.jackson;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

/**
 *
 */
public class DwcTermSerdeTest extends SerdeTestBase<Term> {

  public DwcTermSerdeTest() {
    super(Term.class);
  }

  @Override
  public Term genTestValue() throws Exception {
    return DwcTerm.scientificName;
  }
}
