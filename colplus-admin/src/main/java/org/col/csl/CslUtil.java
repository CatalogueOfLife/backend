package org.col.csl;

import java.io.IOException;

import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.output.Bibliography;
import org.col.api.model.Reference;

public class CslUtil {

  public static String makeBibliography(Reference r) throws IOException {
    r.getCsl().setId("dummy");
    CSLItemData item = CslDataConverter.toCSLItemData(r.getCsl());
    CSLItemData[] items = new CSLItemData[] {item};
    Bibliography b = CSL.makeAdhocBibliography("apa", "text", items);
    r.getCsl().setId(null);
    return b.makeString().trim();
  }

}
