package org.col.parser;

import com.google.common.collect.Lists;
import org.col.api.Name;
import org.col.api.vocab.NameType;
import org.col.api.vocab.Rank;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NameParserGNATest {
  static final NameParserGNA parser = new NameParserGNA();

  @Test
  public void parse() throws Exception {

    assertName("Zophosis persis (Chatanay 1914)", "Zophosis persis")
        .species("Zophosis", "persis")
        .basAuthors("1914", "Chatanay")
        .noCombAuthors();

    assertName("Abies alba Mill.", "Abies alba")
        .species("Abies", "alba")
        .combAuthors(null, "Mill.")
        .noBasAuthors();

    assertName("Festuca ovina L. subvar. gracilis Hackel", "Festuca ovina subvar. gracilis")
        .infraSpecies("Festuca", "ovina", Rank.SUBVARIETY, "gracilis")
        .combAuthors(null,"Hackel")
        .noBasAuthors();

    assertName("Pseudomonas syringae pv. aceris (Ark, 1939) Young, Dye & Wilkie, 1978", "Pseudomonas syringae pv. aceris")
        .infraSpecies("Pseudomonas", "syringae", Rank.PATHOVAR, "aceris")
        .combAuthors("1978","Young", "Dye", "Wilkie")
        .basAuthors("1939", "Ark");

    assertName("Acripeza Guérin-Ménéville 1838", "Acripeza")
        .monomial("Acripeza")
        .combAuthors("1838", "Guérin-Ménéville")
        .noBasAuthors();

    assertName("Alstonia vieillardii Van Heurck & Müll.Arg.", "Alstonia vieillardii")
        .species("Alstonia", "vieillardii")
        .combAuthors(null, "Van Heurck", "Müll. Arg.")
        .noBasAuthors();

    //TODO: do we expect d'urvilleana or durvilleana ???
    assertName("Angiopteris d'urvilleana de Vriese", "Angiopteris durvilleana")
        .species("Angiopteris", "durvilleana")
        .combAuthors(null, "de Vriese")
        .noBasAuthors();
  }

  @Test
  @Ignore("name parser needs to be finished first")
  public void parseUnsupported() throws Exception {

    // fix 4 parted names
    assertName("Acipenser gueldenstaedti colchicus natio danubicus Movchan, 1967", "Acipenser gueldenstaedti natio danubicus")
        .infraSpecies("Acipenser", "gueldenstaedti", Rank.NATIO, "danubicus")
        .combAuthors("1967", "Movchan.");

    // fix cultivar names
    assertName("Acer campestre L. cv. 'nanum'", "Acer campestre", NameType.CULTIVAR)
        .species("Acer", "campestre")
        .combAuthors(null, "L.")
        .noBasAuthors();

    // fix hybrids formulas
    assertName("Asplenium rhizophyllum DC. x ruta-muraria E.L. Braun 1939", "Asplenium rhizophyllum x ruta-muraria", NameType.HYBRID)
        .noAuthors();

    // fix ex authors.
    // In botany (99% of ex author use) the ex author comes first, see https://en.wikipedia.org/wiki/Author_citation_(botany)#Usage_of_the_term_.22ex.22
    assertName("Baccharis microphylla Kunth var. rhomboidea Wedd. ex Sch. Bip. (nom. nud.)", "Baccharis microphylla var. rhomboidea")
        .infraSpecies("Baccharis", "microphylla", Rank.VARIETY, "rhomboidea")
        .combAuthors(null, "Sch. Bip.")
        .noBasAuthors();
  }



  static NameAssertion assertName(String rawName, String sciname) {
    return assertName(rawName, sciname, NameType.SCIENTIFIC);
  }

  static NameAssertion assertName(String rawName, String sciname, NameType type) {
    Name n = parser.parse(rawName).get();
    assertEquals(sciname, n.getScientificName());
    assertEquals(type, n.getType());
    return new NameAssertion(n);
  }

  static class NameAssertion {
    private final Name n;

    public NameAssertion(Name n) {
      this.n = n;
    }

    NameAssertion monomial(String monomial) {
      assertEquals(monomial, n.getScientificName());
      assertNull(n.getGenus());
      assertNull(n.getSpecificEpithet());
      assertNull(n.getInfraspecificEpithet());
      //assertEquals(Rank.SPECIES, n.getRank());
      return this;
    }

    NameAssertion species(String genus, String epithet) {
      assertEquals(genus, n.getGenus());
      assertEquals(epithet, n.getSpecificEpithet());
      assertNull(n.getInfraspecificEpithet());
      assertEquals(Rank.SPECIES, n.getRank());
      return this;
    }
  
    NameAssertion infraSpecies(String genus, String epithet, Rank rank, String infraEpithet) {
      assertEquals(genus, n.getGenus());
      assertEquals(epithet, n.getSpecificEpithet());
      assertEquals(infraEpithet, n.getInfraspecificEpithet());
      assertEquals(rank, n.getRank());
      return this;
    }
  
    NameAssertion combAuthors(String year, String ... authors) {
      assertEquals(year, n.getAuthorship().getCombinationYear());
      assertEquals(Lists.newArrayList(authors), n.getAuthorship().getCombinationAuthors());
      return this;
    }

    NameAssertion basAuthors(String year, String ... authors) {
      assertEquals(year, n.getAuthorship().getOriginalYear());
      assertEquals(Lists.newArrayList(authors), n.getAuthorship().getOriginalAuthors());
      return this;
    }

    NameAssertion noAuthors() {
      noCombAuthors();
      noBasAuthors();
      assertTrue((n.getAuthorship().isEmpty()));
      return this;
    }

    NameAssertion noCombAuthors() {
      assertNull(n.getAuthorship().getCombinationYear());
      assertEquals(Lists.newArrayList(), n.getAuthorship().getCombinationAuthors());
      return this;
    }

    NameAssertion noBasAuthors() {
      assertNull(n.getAuthorship().getOriginalYear());
      assertEquals(Lists.newArrayList(), n.getAuthorship().getOriginalAuthors());
      return this;
    }
  }
}