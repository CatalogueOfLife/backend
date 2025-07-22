package life.catalogue.interpreter;

import life.catalogue.api.model.Identifier;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.parser.GeoTimeParser;
import life.catalogue.parser.SafeParser;

import org.gbif.dwc.terms.Term;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import static life.catalogue.matching.NameValidator.MAX_YEAR;
import static life.catalogue.matching.NameValidator.MIN_YEAR;

public class InterpreterUtils {
  private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{3})(\\d|\\s*\\?)(?:-[0-9-]+)?(?:\\s*[a-zA-Z])?$");
  private static final Pattern SPLIT_COMMA = Pattern.compile("(?<!\\\\),");

  private InterpreterUtils() {}

  /**
   * Strips all html tags if they exist and optionally converts link to markdown links.
   */
  public static String replaceHtml(String x, boolean useMarkdownLinks) {
    if (StringUtils.isBlank(x)) return null;

    var doc = Jsoup.parse(x);
    if (useMarkdownLinks) {
      var links = doc.select("a");
      for (var link : links) {
        String url = link.attr("href");
        if (!StringUtils.isBlank(url)) {
          String md = String.format("[%s](%s)", link.text(), url);
          link.text(md);
        }
      }
    }
    return doc.wholeText().trim();
  }

  public static Integer parseNomenYear(Term term, VerbatimRecord v) {
    return parseNomenYear(v.get(term), v);
  }

  /**
   * Parses the nomenclatural year the name was published and flags issues if the year is unparsable
   * or unlikely, i.e. before 1753 or after next year.
   */
  public static Integer parseNomenYear(String year, IssueContainer issues) {
    if (!StringUtils.isBlank(year)) {
      Matcher m = YEAR_PATTERN.matcher(year.trim());
      if (m.find()) {
        Integer y;
        if (m.group(2).equals("?")) {
          // convert ? to a zero
          y = Integer.parseInt(m.group(1)+"0");
        } else {
          y = Integer.parseInt(m.group(1)+m.group(2));
        }
        if (y < MIN_YEAR || y > MAX_YEAR) {
          issues.add(Issue.UNLIKELY_YEAR);
        } else {
          return y;
        }
      } else {
        issues.add(Issue.UNPARSABLE_YEAR);
      }
    }
    return null;
  }

  public static List<Identifier> interpretIdentifiers(String idsRaw, @Nullable Identifier.Scope defaultScope, IssueContainer issues) {
    if (!StringUtils.isBlank(idsRaw)) {
      List<Identifier> ids = new ArrayList<>();
      for (String altID : SPLIT_COMMA.split(idsRaw)) {
        var id = Identifier.parse(altID);
        ids.add(id);
        if (id.isLocal()) {
          if (defaultScope != null) {
            id.setScope(defaultScope);
          } else {
            issues.add(Issue.IDENTIFIER_WITHOUT_SCOPE);
          }
        }
      }
      return ids;
    }
    return Collections.emptyList();
  }

  public static String normGeoTime(String gt, IssueContainer issues){
    if (gt != null) {
      var pr = SafeParser.parse(GeoTimeParser.PARSER, gt);
      if (pr.isPresent()) {
        return pr.get().getName();
      } else {
        issues.add(Issue.GEOTIME_INVALID);
      }
      return StringUtils.trimToNull(gt.replaceAll("_", " "));
    }
    return null;
  }

  /**
   * Looks for unlikely vernacular names:
   *  - very long strings
   *  - includes common delimiters
   */
  public static boolean unlikelyVernacular(String vname) {
    return vname != null && (
      vname.length() > 100 || vname.contains(",") || vname.contains(";") || vname.contains("|")
    );
  }
}
