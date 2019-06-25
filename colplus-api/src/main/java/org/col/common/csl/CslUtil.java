package org.col.common.csl;

import java.io.IOException;

import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.output.Bibliography;
import org.col.api.model.CslData;
import org.col.api.model.Reference;

public class CslUtil {
  private static final String CITATION_STYLE = "apa";
  private final static ReferenceProvider provider = new ReferenceProvider();
  private final static CSL csl;
  
  static {
    try {
      csl = new CSL(provider, CITATION_STYLE);
      csl.setOutputFormat("text");
    } catch (IOException e) {
      throw new IllegalStateException("APA CSL processor could not be created", e);
    }
  }
  
  static class ReferenceProvider implements ItemDataProvider {
    private static final String ID = "1";
    private CslData data;
    
    public void setData(CslData data) {
      this.data = data;
    }
    
    @Override
    public CSLItemData retrieveItem(String id) {
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
  
  /**
   * WARNING!
   * This is a very slow method that takes a second or more to build the citation string !!!
   * It uses the JavaScript citeproc library internally.
   */
  public static String buildCitation(Reference r) {
    return buildCitation(r.getCsl());
  }
  
  /**
   * WARNING!
   * This is a very slow method that takes a second or more to build the citation string !!!
   * It uses the JavaScript citeproc library internally.
   */
  public static synchronized String buildCitation(CslData data) {
    if (data == null)
      return null;
    
    provider.setData(data);
    csl.registerCitationItems(ReferenceProvider.ID);
    Bibliography bib = csl.makeBibliography();
    
    return bib.getEntries()[0].trim();
  }
  
}
