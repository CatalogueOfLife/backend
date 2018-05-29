package org.col.csl;

import java.io.IOException;
import java.util.List;

import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.ListItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.output.Bibliography;
import de.undercouch.citeproc.output.Citation;
import org.col.api.model.CslData;
import org.col.api.model.Reference;

public class CslUtil {
  private final static ReferenceProvider provider = new ReferenceProvider();
  private final static CSL csl;
  static {
    try {
      csl = new CSL(provider, "apa");
      csl.setOutputFormat("text");
    } catch (IOException e) {
      throw new IllegalStateException("APA CSL processor could not be created", e);
    }
  }

  static class ReferenceProvider implements ItemDataProvider {
    private static final String ID = "1";
    private Reference ref;

    public void setRef(Reference ref) {
      this.ref = ref;
    }

    @Override
    public CSLItemData retrieveItem(String id) {
      CslData data = ref.getCsl();
      final String idOrig = data.getId();
      try {
        data.setId(id);
        CSLItemData itemData = CslDataConverter.toCSLItemData(data);
        return itemData;

      } finally {
        data.setId(idOrig);
      }
    }

    @Override
    public String[] getIds() {
      return new String[]{ID};
    }
  }

  public static synchronized String buildCitation(Reference r) {
    provider.setRef(r);
    csl.registerCitationItems(ReferenceProvider.ID);
    Bibliography bib = csl.makeBibliography();
    return bib.getEntries()[0].trim();
  }

}
