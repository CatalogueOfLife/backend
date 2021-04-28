package life.catalogue.common.csl;

import de.undercouch.citeproc.bibtex.NameParser;
import de.undercouch.citeproc.csl.CSLItemData;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CslFormatterTest {

  @Test
  public void colStyles() throws Exception {
    var d = DatasetNG.read(Resources.stream("metadata/col.yaml"));
    CSLItemData csl = d.toCSL();
    List<CSLItemData> sources = d.source.stream().map(DatasetNG.Citation::toCSL).collect(Collectors.toList());

    List<CslFormatter> styles = Arrays.stream(CslFormatter.STYLE.values()).map(s -> new CslFormatter(s, CslFormatter.FORMAT.TEXT)).collect(Collectors.toList());
    PrintWriter writer = new PrintWriter(System.out);
    for (var style : styles) {
      writer.println(style.style);
      writer.println(style.cite(csl));
      for (CSLItemData item : sources) {
        writer.println(style.cite(item));
      }
      writer.println("\n");
      writer.flush();
    }

    // HTML
    List<CslFormatter> htmlStyles = Arrays.stream(CslFormatter.STYLE.values()).map(s -> new CslFormatter(s, CslFormatter.FORMAT.HTML)).collect(Collectors.toList());
    try (Writer html = UTF8IoUtils.writerFromFile(new File("/tmp/col/cite.html"))) {
      html.write("<html><head><meta charset=\"utf-8\"/></head><body>\n\n");
      for (var style : htmlStyles) {
        html.write("<h4>"+style.style + "</h4>\n");
        html.write("<dl>\n");
        writer.println("<li>" + style.cite(csl) + "</li>\n");
        for (CSLItemData item : sources) {
          writer.println("<li>" + style.cite(item) + "</li>\n");
        }
        html.write("</dl>\n");
        html.write("<hr/>\n");
      }
      html.write("</body></html>\n");
    }
  }

  /**
   * Not a CSL test really, but the BibTeX
   * @throws IOException
   */
  @Test
  public void nameParser() throws IOException {
    var names = NameParser.parse("Barnes and Noble, Inc.");
    System.out.println(names);
    var names2 = NameParser.parse("{{Barnes and Noble, Inc.}}");
    System.out.println(names2);
    var names3 = NameParser.parse("{Catalogue of Life}");
    System.out.println(names3);
  }
}