package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.ReferenceMapStore;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.interpreter.InterpreterUtils;

import org.gbif.nameparser.api.Authorship;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class InterpreterBaseTest {
  
  @Mock
  ReferenceMapStore refStore;

  @Mock
  NeoDb store;

  IssueContainer issues = new IssueContainer.Simple();
  InterpreterBase ib;

  @Before
  public void init() {
    //MockitoAnnotations.initMocks(this);
    when(store.references()).thenReturn(refStore);
    ib = new InterpreterBase(new DatasetSettings(), new ReferenceFactory(1, refStore, null), store, true);
  }

  @Test
  public void replaceHtml() throws Exception {
    assertNull(InterpreterUtils.replaceHtml(null, true));
    assertNull(InterpreterUtils.replaceHtml("", true));
    assertNull(InterpreterUtils.replaceHtml(" ", true));

    assertEquals("x", InterpreterUtils.replaceHtml("x", true));
    assertEquals("x", InterpreterUtils.replaceHtml("x", false));
    assertEquals("Hypoclinea humilis [Mayr, 1868b](https://antcat.org/references/127225)[PDF: 164 (w.) ARGENTINA (Buenos Aires). Neotropic. Primary type information: Primary type material: holotype (?) worker. Primary type locality: Argentina: Buenos Aires, 1866 (Strobel).](https://antcat.org/documents/2148/4369.pdf)",
      InterpreterUtils.replaceHtml("<div class=\"antcat_taxon\">Hypoclinea humilis <a title=\"Mayr, G. 1868b. Formicidae novae Americanae collectae a Prof. P. de Strobel. Annuario della Società dei Naturalisti e Matematici, Modena 3:161-178.\" href=\"https://antcat.org/references/127225\">Mayr, 1868b <a class=\"pdf-link\" rel=\"nofollow\" href=\"https://antcat.org/documents/2148/4369.pdf\">PDF: 164 (w.) ARGENTINA (Buenos Aires). Neotropic. Primary type information: Primary type material: holotype (?) worker. Primary type locality: Argentina: Buenos Aires, 1866 (Strobel).", true));
    assertEquals("Hypoclinea humilis Mayr, 1868b PDF: 164 (w.) ARGENTINA (Buenos Aires). Neotropic. Primary type information: Primary type material: holotype (?) worker. Primary type locality: Argentina: Buenos Aires, 1866 (Strobel).",
      InterpreterUtils.replaceHtml("<div class=\"antcat_taxon\">Hypoclinea humilis <a title=\"Mayr, G. 1868b. Formicidae novae Americanae collectae a Prof. P. de Strobel. Annuario della Società dei Naturalisti e Matematici, Modena 3:161-178.\" href=\"https://antcat.org/references/127225\">Mayr, 1868b <a class=\"pdf-link\" rel=\"nofollow\" href=\"https://antcat.org/documents/2148/4369.pdf\">PDF: 164 (w.) ARGENTINA (Buenos Aires). Neotropic. Primary type information: Primary type material: holotype (?) worker. Primary type locality: Argentina: Buenos Aires, 1866 (Strobel).", false));
    assertEquals("tsn:2134\\,COL:GHS", InterpreterUtils.replaceHtml(" tsn:2134\\,COL:GHS", true));
    assertEquals("tsn:2134\\,COL:GHS", InterpreterUtils.replaceHtml(" tsn:2134\\,COL:GHS", false));
    assertEquals("https://species.wikimedia.org/wiki/Poa_annua", InterpreterUtils.replaceHtml("https://species.wikimedia.org/wiki/Poa_annua", true));
    assertEquals("Hypoclinea humilis [Mayr, 1868b](https://antcat.org/references/127225)[PDF: 164 (w.) ARGENTINA (Buenos Aires). Neotropic. Primary type information: Primary type material: holotype (?) worker. Primary type locality: Argentina: Buenos Aires, 1866 (Strobel).](https://antcat.org/documents/2148/4369.pdf)",
      InterpreterUtils.replaceHtml("Hypoclinea humilis [Mayr, 1868b](https://antcat.org/references/127225)[PDF: 164 (w.) ARGENTINA (Buenos Aires). Neotropic. Primary type information: Primary type material: holotype (?) worker. Primary type locality: Argentina: Buenos Aires, 1866 (Strobel).](https://antcat.org/documents/2148/4369.pdf)", true));
  }

  @Test
  public void identifier() throws Exception {
    IssueContainer issues = IssueContainer.simple();

    var resp = InterpreterUtils.interpretIdentifiers("tsn:2134,col:GHS", null, issues);
    assertEquals(List.of(new Identifier("tsn","2134"), new Identifier("col","GHS")), resp);

    resp = InterpreterUtils.interpretIdentifiers(" tsn:2134, COL:GHS", null, issues);
    assertEquals(List.of(new Identifier("tsn","2134"), new Identifier("col","GHS")), resp);

    resp = InterpreterUtils.interpretIdentifiers(" tsn:2134\\,COL:GHS", null, issues);
    assertEquals(List.of(new Identifier("tsn", "2134\\,COL:GHS")), resp);

    resp = InterpreterUtils.interpretIdentifiers("https://species.wikimedia.org/wiki/Poa_annua", null, issues);
    assertEquals(List.of(new Identifier("https", "//species.wikimedia.org/wiki/Poa_annua")), resp);

    assertFalse(issues.hasIssues());

    resp = InterpreterUtils.interpretIdentifiers("Poa_annua", null, issues);
    assertEquals(List.of(new Identifier(Identifier.Scope.LOCAL, "Poa_annua")), resp);
    assertTrue(issues.hasIssues());
    assertTrue(issues.hasIssue(Issue.IDENTIFIER_WITHOUT_SCOPE));

    issues.clear();
    resp = InterpreterUtils.interpretIdentifiers("wfo-0001057524", Identifier.Scope.WFO, issues);
    assertEquals(List.of(new Identifier(Identifier.Scope.WFO, "wfo-0001057524")), resp);
    assertFalse(issues.hasIssues());
  }

  @Test
  public void secRefPattern() throws Exception {
    assertSecRef("sensu Busch, 1930", "Busch, 1930");
    assertNoSecRef("s.l.");
    assertNoSecRef("s.str.");
    assertNoSecRef("sensu latu");
    assertNoSecRef("sensu lato");
    assertNoSecRef("sensu strict");
    assertNoSecRef("sensu stricto");

    assertNoSecRef("sensu auct.,  non (L.)Mart.");
    assertNoSecRef("sensu auct.,  non Durazz.");
    assertNoSecRef("sensu auctorum");
    assertNoSecRef("sensu auct. non Whittaker");
    assertNoSecRef("auct. nec Whittaker");
    assertNoSecRef("Jurtzev, p.p.");
    assertNoSecRef("(sensu Turcz) Miller");
    assertNoSecRef("(sensu Mereschkowsky, 1878) Jankowski, 1992");

    assertSecRef("sensu Turcz., p.p.", "Turcz., p.p.");
    assertSecRef("auct. Jurtzev, p.p.", "Jurtzev, p.p.");
    assertSecRef("auct. Whittaker 1981", "Whittaker 1981");
  }

  void assertSecRef(String authorship, String expected) {
    Matcher m = InterpreterBase.SEC_REF.matcher(authorship);
    assertTrue(m.find());
    assertEquals(expected, m.group(2));
  }

  void assertNoSecRef(String authorship) {
    assertFalse(InterpreterBase.SEC_REF.matcher(authorship).find());
  }

  @Test
  public void interpretUsage() throws Exception {
    VerbatimRecord v = new VerbatimRecord();

    Name n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setAuthorship("Miller 1876");
    n.setCombinationAuthorship(Authorship.yearAuthors("1876", "Miller"));
    n.rebuildScientificName();

    ParsedNameUsage pnu = new ParsedNameUsage(n, true, "sensu Döring 1999", "Döring 1999. Travels through the Middle East");

    NeoUsage u = ib.interpretUsage(ColdpTerm.ID, pnu, ColdpTerm.status, TaxonomicStatus.ACCEPTED, v, Collections.emptyMap());

    assertTrue(u.usage.isTaxon());
    Taxon t = u.asTaxon();

    assertTrue(t.isExtinct());
    assertNull(t.getNamePhrase());
    // stubbed refstore does assign ids
    //assertNotNull(t.getAccordingToId());

    assertNull(t.getNamePhrase());

    n = t.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Miller 1876", n.getAuthorship());
  }

  @Test
  public void interpretAtomUsage() throws Exception {
    VerbatimRecord v = new VerbatimRecord();

    Name n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setAuthorship("Miller 1876");
    n.setCombinationAuthorship(Authorship.yearAuthors("1876", "Miller"));
    n.rebuildScientificName();

    ParsedNameUsage pnu = new ParsedNameUsage(n);
    pnu.setDoubtful(true); // gets converted to provisional
    NeoUsage u = ib.interpretUsage(ColdpTerm.ID, pnu, ColdpTerm.status, TaxonomicStatus.ACCEPTED, v, Collections.emptyMap());

    assertTrue(u.usage.isTaxon());
    assertEquals(TaxonomicStatus.PROVISIONALLY_ACCEPTED, u.usage.getStatus());
    Taxon t = u.asTaxon();

    assertNull(t.isExtinct());
    assertNull(t.getNamePhrase());

    n = t.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Miller 1876", n.getAuthorship());
  }

  @Test
  public void yearParser() throws Exception {
    for (var x : new String[]{
      "1099",
      "1740",
      "1678",
      "1678b",
      " 1678 b",
      "2100"
    }) {
      issues.getIssues().clear();
      assertNull(x, InterpreterUtils.parseNomenYear(x, issues));
      assertTrue(x, issues.hasIssue(Issue.UNLIKELY_YEAR));
    }

    for (var x : Map.of(
      "1999", 1999,
      "1999b", 1999,
      "184?", 1840,
      " 1999", 1999,
      " 1778 b", 1778,
      "1761 D", 1761
    ).entrySet()) {
      issues.getIssues().clear();
      assertEquals(x.getKey(), x.getValue(), InterpreterUtils.parseNomenYear(x.getKey(), issues));
      assertFalse(x.getKey(), issues.hasIssues());
    }

    for (var x : new String[]{
      "13",
      "-1999",
      "gd2000",
      "1234199",
      "january"
    }) {
      issues.getIssues().clear();
      assertNull(x, InterpreterUtils.parseNomenYear(x, issues));
      assertTrue(x, issues.hasIssue(Issue.UNPARSABLE_YEAR));
    }
  }

  @Test
  public void createDistributions() throws Exception {
    assertDistributions(Gazetteer.ISO, "DE", "DE");
    assertDistributions(Gazetteer.ISO, "DE,fr,es", "DE", "FR", "ES");
    assertDistributions(Gazetteer.ISO, "az-tar; AZ; ", "AZ", "AZ");
    // ignore unparsable regions
    assertDistributions(Gazetteer.ISO, "Bolivia (Chuquisaca, Cochabamba, Santa Cruz, Tarija), Peru, Colombia (Amazonas, Antioquia, Boyac, Cauca, Choc, Cundinamarca, Huila, La Guajira, Magdalena, Meta, Nario, Putumayo, Risaralda, Santander, Valle), NE-Brazil (Pernambuco, Bahia, Alagoas), WC-Brazil (Goias, Distrito Federal, Mato Grosso do Sul), SE-Brazil (Minas Gerais, Espirito Santo, Sao Paulo, Rio de Janeiro), Ecuador, Galapagos, Mexico (Hidalgo, Michoacan, Oaxaca, Tamaulipas, Veracruz), Venezuela (Tachira), Argentina (Buenos Aires, Chaco, Chubut, Cordoba, Corrientes, Entre Rios, Formosa, Jujuy, Misiones,Rio Negro, Salta, Santa Fe, Tucuman), S-Brazil (Parana, Rio Grande do Sul, Santa Catarina), Paraguay (Caazapa, Central, Cordillera, Guaira, Paraguari), Uruguay (Artigas, Canelones, Cerro Largo, Colonia, Durazno, Flores Prov., Florida Prov., Lavalleja, Maldonado, Montevideo, Paysandu, Rio Negro, Rivera, Rocha, Salto, San Jose, Soriano, Tacuarembo, Treinta y Tres), Costa Rica (I)",
            Country.BOLIVIA, Country.PERU, Country.COLOMBIA, Country.ECUADOR, Country.MEXICO,
            Country.VENEZUELA, Country.ARGENTINA, Country.PARAGUAY, Country.URUGUAY, Country.COSTA_RICA);
    // MRGID URLs, see https://github.com/CatalogueOfLife/data/issues/483
    assertDistributions(Gazetteer.MRGID, "http://marineregions.org/mrgid/48213", "48213");
    assertDistributions(Gazetteer.MRGID, "https://marineregions.org/mrgid/48213", "48213");
  }

  private void assertDistributions(Gazetteer std, String loc, Country... expected) {
    String[] exp = Arrays.stream(expected).map(Country::getIso2LetterCode).toArray(String[]::new);
    assertDistributions(std, loc, exp);
  }

  private void assertDistributions(Gazetteer std, String loc, String... expectedIDs) {
    List<Distribution> dis = InterpreterBase.createDistributions(std, loc, "present", new VerbatimRecord(), null, new BiConsumer<Distribution, VerbatimRecord>() {
      @Override
      public void accept(Distribution distribution, VerbatimRecord verbatimRecord) {
        // dont do anything
      }
    });

    int counter = 0;
    for (Distribution d : dis) {
      assertEquals(std, d.getArea().getGazetteer());
      assertEquals(expectedIDs[counter++], d.getArea().getId());
    }
  }
}
