package org.col.csl;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.HttpClients;
import org.col.api.model.CslData;
import org.col.api.vocab.CSLRefType;
import org.junit.Ignore;
import org.junit.Test;

import static com.fasterxml.jackson.core.util.DefaultIndenter.SYS_LF;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("static-method")
public class AnystyleParserWrapperTest {

  //@Test
  @Ignore
  public void testParse01() throws Exception {
    try (AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault())) {
      parser.start();
      String ref = "Perec, Georges. A Void. London: The Harvill Press, 1995. p.108.";
      CslData item = parser.parse(ref).get();
      System.out.println(pretty(item));
      assertEquals("perec1995a", item.getId());
      assertEquals(CSLRefType.BOOK, item.getType());
      assertEquals("Perec", item.getAuthor()[0].getFamily());
      assertEquals("Georges", item.getAuthor()[0].getGiven());
      assertEquals(1995, item.getIssued().getDateParts()[0][0]);
      assertEquals("108", item.getPage());
      assertEquals("The Harvill Press", item.getPublisher());
      assertEquals("London", item.getPublisherPlace());
      assertEquals("A Void", item.getTitle());
    }
  }

  @Test
  @Ignore
  // ACEF 50, PSF-50567
  public void testParse02() throws Exception {
    try (AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault())) {
      parser.start();
      String ref = "Bulletin of Hong Kong Entomological Society 8(1):3-7";
      CslData item = parser.parse(ref).get();
      System.out.println(pretty(item));
    }
  }

  @Test
  @Ignore
  // ACEF 50, PSF-46401
  public void testParse03() throws Exception {
    try (AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault())) {
      parser.start();
      String ref = "Acta Zootaxonomica Sinica 38(3):525-527";
      CslData item = parser.parse(ref).get();
      System.out.println(pretty(item));
    }
  }
  
  @Test
  @Ignore
  // DWCA brentids, 1752
  public void testParse04() throws Exception {
    try (AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault())) {
      parser.start();
      String ref = "In:  Schoenherr C. J. 1840 -  Genera et species curculionidum, cum synonymia hujus familiae. Species novae aut hactenus minus cognitae, descriptionibus a Dom. Leonardo Gyllenhal, C. H. Boheman, et entomologis aliis illustratae, Vol. 5(2) Supplementum cont";
      CslData item = parser.parse(ref).get();
      System.out.println(pretty(item));
    }
  }
  
  @Test
  @Ignore
  // DWCA brentids, 1722
  public void testParse05() throws Exception {
    try (AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault())) {
      parser.start();
      String ref = "In:  Sforzi A. & Bartolozzi L. 2004 -  Brentidae of the world (Coleoptera, Curculionoidea), XXXIX. Monografie del Museo Regionale di Scienze Naturali, Turin(Italy). p. 19-828.\n";
      CslData item = parser.parse(ref).get();
      System.out.println(pretty(item));
    }
  }

  @Test
  // @Ignore
  // Example from http://docs.citationstyles.org/en/stable/primer.html
  public void testParse06() throws Exception {
    try (AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault())) {
      parser.start();
      String ref = "Gidijala L, Bovenberg RA, Klaassen P, van der Klei IJ, Veenhuis M, et al. (2008) Production of functionally active Penicillium chrysogenum isopenicillin N synthase in the yeast Hansenula polymorpha. BMC Biotechnol 8: 29.";
      CslData item = parser.parse(ref).get();
      System.out.println(pretty(item));
    }
  }

  public static String pretty(Object obj) throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper();
    om.setSerializationInclusion(Include.NON_NULL);
    return om.writer(getPrettyPrinter()).writeValueAsString(obj);
  }

  private static DefaultPrettyPrinter getPrettyPrinter() {
    DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter("    ", SYS_LF);
    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
    printer.indentObjectsWith(indenter);
    printer.indentArraysWith(indenter);
    return printer;
  }
}
