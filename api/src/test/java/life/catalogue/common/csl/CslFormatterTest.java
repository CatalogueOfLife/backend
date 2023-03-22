package life.catalogue.common.csl;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.undercouch.citeproc.bibtex.NameParser;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLNameBuilder;
import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CslFormatterTest {

  @Test
  public void colStyles() throws Exception {
    var d = Dataset.read(Resources.stream("metadata/col.yaml"));
    CSLItemData csl = d.toCSL();
    List<CSLItemData> sources = d.getSource().stream().map(Citation::toCSL).collect(Collectors.toList());

    List<CslFormatter> styles = Arrays.stream(CslFormatter.STYLE.values()).map(s -> new CslFormatter(s, CslFormatter.FORMAT.HTML)).collect(Collectors.toList());
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

  @Test
  public void datasetCitation() throws JsonProcessingException {
    CSLItemDataBuilder builder = new CSLItemDataBuilder()
      .type(CSLType.DATASET)
      .title("The World Checklist of Vascular Plants (WCVP): Fabaceae")
      .issued(2021, 5, 16)
      .accessed(1999)
      .author(
        new CSLNameBuilder().given("Giovani Carlos").family("Andrella").build(),
        new CSLNameBuilder().given("Margoth").family("Atahuachi Burgos").build()
      )
      .editor("Rafaël", "Govaerts")
      .DOI("10.1093/database/baw125")
      .URL("gbif.org")
      .ISSN("1758-0463")
      .version("1.0");

    var format = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.HTML);
    System.out.println(ApiModule.MAPPER.writeValueAsString(builder.build()));

    String x = format.cite(builder.build());
    System.out.println(x);
    assertFalse(x.contains("[Data set]"));
  }

  @Test
  public void pureDOI() throws JsonProcessingException {
    CSLItemDataBuilder builder = new CSLItemDataBuilder()
      .type(CSLType.ARTICLE_JOURNAL)
      .DOI("10.1093/database/baw125");

    var text = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT);
    var html = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.HTML);

    String x = text.cite(builder.build());
    System.out.println(x);
    assertFalse(x.contains("(n.d.)"));

    x = html.cite(builder.build());
    System.out.println(x);
    assertFalse(x.contains("(n.d.)"));
  }

  @Test
  public void bookChapter() throws JsonProcessingException {
    CSLItemDataBuilder builder = new CSLItemDataBuilder()
      .type(CSLType.CHAPTER)
      .title("The Diary of a Young Girl and children's literature of atrocity.")
      .author("S", "Minslow")
      .issued(2017)
      .containerTitle("Critical Insights: The Diary of a Young Girl")
      .publisher("Grey House")
      .page("60-75")
      ;
    var text = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT);

    assertEquals("Minslow, S. (2017). The Diary of a Young Girl and children’s literature of atrocity. In Critical Insights: The Diary of a Young Girl (pp. 60–75). Grey House.", text.cite(builder.build()));

    builder.containerAuthor("Markus","Döring");
    assertEquals("Minslow, S. (2017). The Diary of a Young Girl and children’s literature of atrocity. In M. Döring, Critical Insights: The Diary of a Young Girl (pp. 60–75). Grey House.", text.cite(builder.build()));

    builder.editor("Ewald","Döring");
    assertEquals("Minslow, S. (2017). The Diary of a Young Girl and children’s literature of atrocity. In E. Döring (Ed.), Critical Insights: The Diary of a Young Girl (pp. 60–75). Grey House.", text.cite(builder.build()));
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