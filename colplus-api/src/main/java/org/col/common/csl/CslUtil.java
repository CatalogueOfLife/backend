package org.col.common.csl;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final AtomicInteger counter = new AtomicInteger();
    private Map<String, CslData> data = new ConcurrentHashMap<>();
    
    public String setData(CslData data) {
      String key = String.valueOf(counter.incrementAndGet());
      this.data.put(key, data);
      return key;
    }
    
    @Override
    public CSLItemData retrieveItem(String id) {
      CslData item = data.remove(id);
      final String idOrig = item.getId();
      try {
        item.setId(id);
        return CslDataConverter.toCSLItemData(item);
        
      } finally {
        item.setId(idOrig);
      }
    }
    
    @Override
    public String[] getIds() {
      return data.keySet().stream().map(String::toString).toArray(String[]::new);
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
    String key = provider.setData(data);
    csl.registerCitationItems(key);
    return csl.makeBibliography().getEntries()[0].trim();
  }
  
  
  
  
}
