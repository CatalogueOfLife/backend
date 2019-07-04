package org.col.common.csl;

import java.io.IOException;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import org.col.api.model.CslData;
import org.col.api.model.Reference;

public class CslUtil {
  private static final String CITATION_STYLE = "apa";
  private final static ReferenceProvider provider = new ReferenceProvider();
  private final static CSL csl;
  private static Timer timer;
  
  static {
    try {
      csl = new CSL(provider, CITATION_STYLE);
      csl.setOutputFormat("text");
    } catch (IOException e) {
      throw new IllegalStateException("APA CSL processor could not be created", e);
    }
  }
  
  public static void register(MetricRegistry registry) {
    timer = registry.timer("org.col.csl.citation-builder");
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
  public static String buildCitation(CslData data) {
    if (data == null)
      return null;
  
    Timer.Context ctx = timer == null ? null : timer.time();
    try {
      return buildCitationInternal(data);
    } finally {
      if (ctx != null) {
        ctx.stop();
      }
    }
  }
  
  private static synchronized String buildCitationInternal(CslData data) {
    provider.setData(data);
    csl.registerCitationItems(ReferenceProvider.ID);
    return csl.makeBibliography().getEntries()[0].trim();
  }
  
  
  
  
}
