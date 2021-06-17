package life.catalogue.common.csl;

import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import life.catalogue.api.model.CslData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A formatter for single CSL items according to a specific citation style.
 * Reuse the instance for the same style or consider to use the static CslUtils!
 */
public class CslFormatter {
  private static final Logger LOG = LoggerFactory.getLogger(CslFormatter.class);
  private final SingleItemProvider provider = new SingleItemProvider();
  private final CSL csl;
  public final FORMAT format;
  public final STYLE style;

  public enum FORMAT {TEXT, HTML, RTF}
  public enum STYLE {APA, CSE, IEEE, MLA, CHICAGO, HARVARD, EJT, TAXON}

  static class SingleItemProvider implements ItemDataProvider {
    private static final String KEY = "1";
    private CSLItemData data;

    public String setData(CslData data) {
      final String idOrig = data.getId();
      try {
        data.setId(KEY);
        this.data = CslDataConverter.toCSLItemData(data);
      } finally {
        data.setId(idOrig);
      }
      return KEY;
    }

    public String setData(CSLItemData data) {
      CSLItemDataBuilder builder = new CSLItemDataBuilder(data);
      builder.id(KEY);
      this.data = builder.build();
      return KEY;
    }

    @Override
    public CSLItemData retrieveItem(String id) {
      return data;
    }

    @Override
    public String[] getIds() {
      return new String[]{KEY};
    }
  }

  public CslFormatter(STYLE style, FORMAT format) {
    this.format = format;
    this.style = style;
    try {
      csl = new CSL(provider, "/csl-styles/" + style.name().toLowerCase() + ".csl");
      csl.setOutputFormat(format.name().toLowerCase());
    } catch (IOException e) {
      throw new IllegalStateException(style + " CSL processor could not be created", e);
    }
  }

  /**
   * A synchronised method to build a single citation string in the formatters citation style.
   * The method is thread safe and although the method is rather slow it had to be synchronized in its entire call.
   * The first time this is called it is rather slow.
   */
  public synchronized String cite(CslData data) {
    String key = provider.setData(data);
    return cite(key);
  }

  public synchronized String cite(CSLItemData data) {
    String key = provider.setData(data);
    return cite(key);
  }

  private String cite(String key){
    csl.registerCitationItems(key);
    try {
      String[] entries = csl.makeBibliography().getEntries();
      return entries == null || entries.length==0 ? null : entries[0].trim();

    } catch (RuntimeException e) {
      LOG.warn("Failed to create citation", e);
      return null;
    }
  }
}
