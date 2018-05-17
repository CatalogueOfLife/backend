package org.col.csl;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.HttpClients;
import org.col.admin.config.AnystyleConfig;
import org.col.api.model.CslData;
import org.col.api.vocab.CSLRefType;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static com.fasterxml.jackson.core.util.DefaultIndenter.SYS_LF;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("static-method")
public class AnystyleParserWrapperTest {

  private static AnystyleConfig cfg;

  @BeforeClass
  public static void init() {
    cfg = new AnystyleConfig();
    // TODO read from anystyle-test.yaml
    cfg.baseUrl = "http://localhost:4567";
  }

  @Test
  @Ignore
  public void testParse01() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
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

  @Test
  @Ignore
  // ACEF 50, PSF-50567
  public void testParse02() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref = "Bulletin of Hong Kong Entomological Society 8(1):3-7";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
  }

  @Test
  @Ignore
  // ACEF 50, PSF-46401
  public void testParse03() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref = "Acta Zootaxonomica Sinica 38(3):525-527";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
  }

  @Test
  @Ignore
  // DWCA brentids, 1752
  public void testParse04() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref =
        "In:  Schoenherr C. J. 1840 -  Genera et species curculionidum, cum synonymia hujus familiae. Species novae aut hactenus minus cognitae, descriptionibus a Dom. Leonardo Gyllenhal, C. H. Boheman, et entomologis aliis illustratae, Vol. 5(2) Supplementum cont";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
  }

  @Test
  @Ignore
  // DWCA brentids, 1722
  public void testParse05() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref =
        "In:  Sforzi A. & Bartolozzi L. 2004 -  Brentidae of the world (Coleoptera, Curculionoidea), XXXIX. Monografie del Museo Regionale di Scienze Naturali, Turin(Italy). p. 19-828.\n";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
  }

  @Test
  @Ignore
  // Example from http://docs.citationstyles.org/en/stable/primer.html
  public void testParse06() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref =
        "Gidijala L, Bovenberg RA, Klaassen P, van der Klei IJ, Veenhuis M, et al. (2008) Production of functionally active Penicillium chrysogenum isopenicillin N synthase in the yeast Hansenula polymorpha. BMC Biotechnol 8: 29.";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
  }

  @Test
  @Ignore
  // With HTML markup
  public void testParse07() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref =
        "Laubenfels, M.W. de. (1930). TheSponges of California. (Abstracts of dissertations for the degree of doctor of philosophy. <em>Stanford University Bulletin.</em> 5(98): 24-29.";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
  }

  // FLO-3122","Stroinski A. & Swierczewski D.","2013","Peyrierasus gen. nov. - a new genus of
  // Flatidae (Hemiptera: Fulgoromorpha) from Southeastern Madagascar","Annales Zoologici, 63(2):
  // 251-262.

  @Test
  @Ignore
  public void testParse08() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref =
        "Bulletin de la Société d'Histoire Naturelle de l'Afrique du Nord. Alger, XIV: 173-176.";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
  }

  @Test
  @Ignore
  public void testParse09() throws Exception {
    AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault(), cfg);
    String ref = "Some species of Delphacodes (Homoptera, Fulgoridae, Delphacinae). Part IV";
    CslData item = parser.parse(ref).get();
    System.out.println(pretty(item));
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
