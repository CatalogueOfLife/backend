package life.catalogue.common.csl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.Reference;

public class CslUtil {
  private final static CslFormatter apa = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT);
  private static Timer timer;
  
  public static void register(MetricRegistry registry) {
    timer = registry.timer("life.catalogue.csl.citation-builder");
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
   * This is a rather slow method that takes ~20-50ms to build the citation string !!!
   * It uses the JavaScript citeproc library internally.
   */
  public static String buildCitation(CslData data) {
    if (data == null)
      return null;
  
    Timer.Context ctx = timer == null ? null : timer.time();
    try {
      return apa.cite(data);
    } finally {
      if (ctx != null) {
        ctx.stop();
      }
    }
  }

  
  
  
  
}
