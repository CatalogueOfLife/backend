package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Issue;
import life.catalogue.importer.neo.ReferenceStore;
import life.catalogue.importer.reference.ReferenceFactory;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

public class InterpreterBaseTest {
  
  @Mock
  ReferenceStore refStore;
  IssueContainer issues = new VerbatimRecord();
  InterpreterBase inter = new InterpreterBase(new DatasetSettings(), new ReferenceFactory(1, refStore), null);

  @Test
  public void secRefPattern() throws Exception {
    assertSecRef("sensu Busch, 1930", "Busch, 1930");
    assertSecRef("Miller sensu Busch, 1930", "Busch, 1930");
    assertNoSecRef("s.l.");
    assertNoSecRef("s.str.");
    assertNoSecRef("sensu latu");
    assertNoSecRef("sensu lato");
    assertNoSecRef("sensu strict");
    assertNoSecRef("sensu stricto");
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
  public void interpretName() throws Exception {
    InterpreterBase ib = new InterpreterBase(new DatasetSettings(), null, null);
    VerbatimRecord v = new VerbatimRecord();
    Optional<ParsedNameUsage> nat = ib.interpretName(true, "1", "species", "Picea arlba", "Mill. and Desbrochers de Loges, 1881", "Abies", null, "alba", null, null, null, null, null, null, v);
    Name n = nat.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. and Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // if atoms are given they take precedence over the full name
    nat = ib.interpretName(true, "1", "species", "Picea arlba Mill. 2121", "", "Abies", null, "alba", null, null, null, null, null, null, v);
    n = nat.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertNull(n.getAuthorship());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertNull(n.getCombinationAuthorship().getYear());

    // if no authorahip is given it needs to be rebuild
    nat = ib.interpretName(true, "1", "species", "Abies alba Mill. and Desbrochers de Loges, 1881", "", "", null, null, null, null, null, null, null, null, v);
    n = nat.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // if no authorahip is given it needs to be rebuild
    nat = ib.interpretName(true, "1", "species", "Abies alba Mill. and Desbrochers de Loges, 1881", "", "", null, null, null, null, null, null, null, null, v);
    n = nat.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // exclude taxon notes from authorship
    nat = ib.interpretName(true, "1", "species", "Abies alba Mill. and Desbrochers de Loges, 1881 sensu Döring 1999", "", "", null, null, null, null, null, null, null, null, v);
    assertEquals("sensu Döring 1999", nat.get().getTaxonomicNote());
    n = nat.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    nat = ib.interpretName(true, "1", "species", "Abies alba", "Mill. and Desbrochers de Loges, 1881 sensu Döring 1999","", null, null, null, null, null, null, null, null, v);
    assertEquals("sensu Döring 1999", nat.get().getTaxonomicNote());
    n = nat.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. and Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());
  }

  @Test
  public void yearParser() throws Exception {
    assertEquals((Integer) 1678, InterpreterBase.parseYear("1678", issues));
    assertFalse(issues.hasIssues());
  
    assertEquals((Integer) 1678, InterpreterBase.parseYear("1678b", issues));
    assertFalse(issues.hasIssues());
  
    assertEquals((Integer) 1678, InterpreterBase.parseYear(" 1678 b", issues));
    assertFalse(issues.hasIssues());
    
    assertEquals((Integer) 999, InterpreterBase.parseYear("999", issues));
    assertTrue(issues.hasIssue(Issue.UNLIKELY_YEAR));
    
    issues.getIssues().clear();
    assertEquals((Integer) 2112, InterpreterBase.parseYear("2112", issues));
    assertTrue(issues.hasIssue(Issue.UNLIKELY_YEAR));
  
    issues.getIssues().clear();
    assertEquals((Integer) 2800, InterpreterBase.parseYear("2800", issues));
    assertTrue(issues.hasIssue(Issue.UNLIKELY_YEAR));

    issues.getIssues().clear();
    assertEquals((Integer) 1980, InterpreterBase.parseYear("198?", issues));
    assertFalse(issues.hasIssues());
  
    issues.getIssues().clear();
    assertNull(InterpreterBase.parseYear("gd2000", issues));
    assertTrue(issues.hasIssue(Issue.UNPARSABLE_YEAR));
  
    issues.getIssues().clear();
    assertNull(InterpreterBase.parseYear("35611", issues));
    assertTrue(issues.hasIssue(Issue.UNPARSABLE_YEAR));
  
    issues.getIssues().clear();
    assertNull(InterpreterBase.parseYear("january", issues));
    assertTrue(issues.hasIssue(Issue.UNPARSABLE_YEAR));
  }

  @Test
  public void createDistributions() throws Exception {
    assertDistributions(Gazetteer.ISO, "DE", "DE");
    assertDistributions(Gazetteer.ISO, "DE,fr,es", "DE", "FR", "ES");
    assertDistributions(Gazetteer.ISO, "az-tar; AZ; ", "AZ-TAR", "AZ");
    // ignore unparsable regions
    assertDistributions(Gazetteer.ISO, "Bolivia (Chuquisaca, Cochabamba, Santa Cruz, Tarija), Peru, Colombia (Amazonas, Antioquia, Boyac, Cauca, Choc, Cundinamarca, Huila, La Guajira, Magdalena, Meta, Nario, Putumayo, Risaralda, Santander, Valle), NE-Brazil (Pernambuco, Bahia, Alagoas), WC-Brazil (Goias, Distrito Federal, Mato Grosso do Sul), SE-Brazil (Minas Gerais, Espirito Santo, Sao Paulo, Rio de Janeiro), Ecuador, Galapagos, Mexico (Hidalgo, Michoacan, Oaxaca, Tamaulipas, Veracruz), Venezuela (Tachira), Argentina (Buenos Aires, Chaco, Chubut, Cordoba, Corrientes, Entre Rios, Formosa, Jujuy, Misiones,Rio Negro, Salta, Santa Fe, Tucuman), S-Brazil (Parana, Rio Grande do Sul, Santa Catarina), Paraguay (Caazapa, Central, Cordillera, Guaira, Paraguari), Uruguay (Artigas, Canelones, Cerro Largo, Colonia, Durazno, Flores Prov., Florida Prov., Lavalleja, Maldonado, Montevideo, Paysandu, Rio Negro, Rivera, Rocha, Salto, San Jose, Soriano, Tacuarembo, Treinta y Tres), Costa Rica (I)",
            Country.BOLIVIA, Country.PERU, Country.COLOMBIA, Country.ECUADOR, Country.MEXICO,
            Country.VENEZUELA, Country.ARGENTINA, Country.PARAGUAY, Country.URUGUAY, Country.COSTA_RICA);
  }

  private void assertDistributions(Gazetteer std, String loc, Country... expected) {
    String[] exp = Arrays.stream(expected).map(Country::getIso2LetterCode).toArray(String[]::new);
    assertDistributions(std, loc, exp);
  }

  private void assertDistributions(Gazetteer std, String loc, String... expected) {
    List<Distribution> dis = InterpreterBase.createDistributions(std, loc, "present", new VerbatimRecord(), new BiConsumer<Distribution, VerbatimRecord>() {
      @Override
      public void accept(Distribution distribution, VerbatimRecord verbatimRecord) {
        // dont do anything
      }
    });

    int counter = 0;
    for (Distribution d : dis) {
      assertEquals(std, d.getGazetteer());
      assertEquals(expected[counter++], d.getArea());
    }
  }
}
