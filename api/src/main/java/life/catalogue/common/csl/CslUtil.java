package life.catalogue.common.csl;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.Reference;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.BibTeXFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.undercouch.citeproc.csl.CSLItemData;

public class CslUtil {
  private static final Logger LOG = LoggerFactory.getLogger(CslUtil.class);
  private final static CslFormatter apaHtml = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.HTML);
  private final static CslFormatter apaText = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT);
  private final static Pattern VOLUME_ISSUE_PAGE = Pattern.compile("^(.*?)\\s*(\\d+)\\s*(?:\\(\\s*(\\d+)\\s*\\))?\\s*(?::\\s*(?:(?:p|pp|page)\\.?\\s*)?(\\d+))?\\s*$");

  /**
   * WARNING!
   * This is a very slow method that takes a second or more to build the citation string !!!
   * It uses the JavaScript citeproc library internally.
   */
  public static String buildCitation(Reference r) {
    return buildCitation(r.getCsl());
  }
  
  public static String buildCitation(CslData data) {
    if (data != null) {
      return apaText.cite(data);
    }
    return null;
  }

  public static String buildCitation(CSLItemData data) {
    if (data != null) {
      return apaText.cite(data);
    }
    return null;
  }

  public static String buildCitationHtml(CSLItemData data) {
    if (data != null) {
      return apaHtml.cite(data);
    }
    return null;
  }

  /**
   * Primarily exposes the entry formatting method - we dont deal with databases and lists
   */
  static class BibTeXFormatter2 extends BibTeXFormatter {
    public void format(BibTeXEntry entry, Writer writer) throws IOException {
      super.format(entry, writer);
    }
  }

  public static String toBibTexString(CSLItemData data) {
    try {
      BibTeXFormatter2 formatter = new BibTeXFormatter2();
      var w = new StringWriter();
      formatter.format(CslDataConverter.toBibTex(data), w);
      return w.toString();

    } catch (IOException e) {
      // how did this happen to StringWriter?
      LOG.error("Failed to write BibTeX from csl data", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Produces semicolon delimited lists of the following form usable for ColDP CSV files:
   * family1, given1; family2, given2; ...
   */
  public static String toColdpString(CslName[] data) {
    if (data != null && data.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (var n : data) {
        if (sb.length()>0) {
          sb.append("; ");
        }
        if (n.isLiteralOnly()) {
          sb.append(n.getLiteral());

        } else {
          if (n.getNonDroppingParticle() != null) {
            sb.append(n.getNonDroppingParticle());
            sb.append(" ");
          }
          sb.append(n.getFamily());
          if (n.getGiven() != null) {
            sb.append(",");
            sb.append(n.getGiven());
          }
        }
      }
      return sb.toString();
    }
    return null;
  }

  public static Optional<VolumeIssuePage> parseVolumeIssuePage(String volIssuePage) {
    if (volIssuePage != null) {
      Matcher m = VOLUME_ISSUE_PAGE.matcher(volIssuePage);
      if (m.find()) {
        return Optional.of(new VolumeIssuePage(m.group(1), toInt(m, 2), toInt(m, 3), toInt(m, 4)));
      }
    }
    return Optional.empty();
  }

  private static Integer toInt(Matcher m, int group) {
    if (m.group(group) != null) {
      return Integer.valueOf(m.group(group));
    }
    return null;
  }

  public static class VolumeIssuePage {
    public final String beginning;
    public final Integer volume;
    public final Integer issue;
    public final Integer page;

    public VolumeIssuePage(String beginning, Integer volume, Integer issue, Integer page) {
      this.beginning = StringUtils.trimToNull(beginning);
      this.volume = volume;
      this.issue = issue;
      this.page = page;
    }

    public boolean hasBeginning() {
      return beginning != null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof VolumeIssuePage)) return false;
      VolumeIssuePage that = (VolumeIssuePage) o;
      return Objects.equals(beginning, that.beginning)
             && Objects.equals(volume, that.volume)
             && Objects.equals(issue, that.issue)
             && Objects.equals(page, that.page);
    }

    @Override
    public int hashCode() {
      return Objects.hash(beginning, volume, issue, page);
    }
  }
}
