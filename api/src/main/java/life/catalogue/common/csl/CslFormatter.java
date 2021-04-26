package life.catalogue.common.csl;

import de.undercouch.citeproc.CSL;
import life.catalogue.api.model.CslData;

import java.io.IOException;

/**
 * A formatter for single CSL items according to a specific citation style.
 * Reuse the instance for the same style or consider to use the static CslUtils!
 */
public class CslFormatter {
  private final CslUtil.ReferenceProvider provider = new CslUtil.ReferenceProvider();
  private final CSL csl;
  public final FORMAT format;
  public final STYLE style;

  public enum FORMAT {TEXT, HTML, RTF}
  public enum STYLE {APA, CSE, IEEE, MLA, CHICAGO, NATURE}

  public CslFormatter(STYLE style, FORMAT format) {
    this.format = format;
    this.style = style;
    try {
      csl = new CSL(provider, "/csl-styles/" + style + ".csl");
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
    csl.registerCitationItems(key);
    return csl.makeBibliography().getEntries()[0].trim();
  }

}
