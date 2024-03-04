package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.Rank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class NameInterpreterTest {
  
  NameInterpreter ib;

  @Before
  public void init() {
    ib = new NameInterpreter(new DatasetSettings(), true);
  }

  void assertOenantheL(ParsedNameUsage pnu) {
    var n = pnu.getName();
    assertEquals("Oenanthe", n.getScientificName());
    assertEquals("L.", n.getAuthorship());
    assertEquals(Rank.UNRANKED, n.getRank());
    assertEquals("Oenanthe", n.getUninomial());
    assertNull(n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertNull(n.getSpecificEpithet());
    assertNull(n.getInfraspecificEpithet());
  }

  ParsedNameUsage interpret(String rank, String sciname, String authorship, VerbatimRecord v){
    return ib.interpret("1", rank, null,
      sciname, authorship,null,
      null, null, null, null, null, null,
      null,null,null,null,null,null,
      null, null, null, null,
      null, null, null, v
      ).get();
  }

  ParsedNameUsage interpret(String rank, String sciname, String authorship,
                            String link, String remarks, String identifiers, VerbatimRecord v){
    v.setTerms(vmap(
      ColdpTerm.link, link,
      ColdpTerm.remarks, remarks,
      ColdpTerm.alternativeID, identifiers
    ));
    return ib.interpret("1", rank, null,
      sciname, authorship,null,
      null, null, null, null, null, null,
      null,null,null,null,null,null,
      null, null, null, null,
      ColdpTerm.link, ColdpTerm.remarks, ColdpTerm.alternativeID, v
    ).get();
  }

  ParsedNameUsage interpret(String rank, String sciname, String authorship, String publishedInYear,
                            String uninomial, String genus, String infraGenus, String species, String infraspecies, VerbatimRecord v
  ){
    return interpret(rank, sciname, authorship, publishedInYear, uninomial, genus, infraGenus, species, infraspecies, null,
      null,null,null,null,null,null,
      null, null, null, null, v);
  }

  ParsedNameUsage interpret(String rank, String sciname, String authorship, String publishedInYear,
                            String uninomial, String genus, String infraGenus, String species, String infraspecies, String cultivar,
                            String notho, String originalSpelling, String nomCode, String nomStatus, VerbatimRecord v
  ){
    return interpret(rank, sciname, authorship, publishedInYear, uninomial, genus, infraGenus, species, infraspecies, cultivar,
      null,null,null,null,null,null,
      notho, originalSpelling, nomCode, nomStatus, v);
  }

  ParsedNameUsage interpret(String rank, String sciname, String authorship, String publishedInYear,
                            String uninomial, String genus, String infraGenus, String species, String infraspecies, String cultivar,
                            String combAuthors, String combExAuthors, String combAuthorsYear, String basAuthors, String basExAuthors, String basAuthorsYear,
                            String notho, String originalSpelling, String nomCode, String nomStatus, VerbatimRecord v
  ){
    v.setTerms(vmap(
      ColdpTerm.combinationAuthorship, combAuthors,
      ColdpTerm.combinationExAuthorship, combExAuthors,
      ColdpTerm.combinationAuthorshipYear, combAuthorsYear,
      ColdpTerm.basionymAuthorship, basAuthors,
      ColdpTerm.basionymExAuthorship, basExAuthors,
      ColdpTerm.basionymAuthorshipYear, basAuthorsYear,
      ColdpTerm.notho, notho,
      ColdpTerm.originalSpelling, originalSpelling,
      ColdpTerm.code, nomCode,
      ColdpTerm.status, nomStatus
    ));
    v.clear();
    return ib.interpret("1", rank, null,
      sciname, authorship,publishedInYear,
      uninomial, genus, infraGenus, species, infraspecies, cultivar,
      ColdpTerm.combinationAuthorship, ColdpTerm.combinationExAuthorship, ColdpTerm.combinationAuthorshipYear, ColdpTerm.basionymAuthorship, ColdpTerm.basionymExAuthorship, ColdpTerm.basionymAuthorshipYear,
      ColdpTerm.notho, ColdpTerm.originalSpelling, ColdpTerm.code, ColdpTerm.status,
      null, null, null, v
    ).get();
  }

  Map<Term, String> vmap(Object ... args) {
    var map = new HashMap<Term, String>();
    for (int i = 0; i < args.length; i=i+2) {
      Object val = args[i+1];
      if (val != null) {
        map.put((Term)args[i], (String)val);
      }
    }
    return map;
  }

  @Test
  public void interpretName() throws Exception {
    VerbatimRecord v = new VerbatimRecord();
    ParsedNameUsage pnu;
    Name n;

    pnu = ib.interpret(SimpleName.sn("Barleeidae [sic]"), v).get();
    assertTrue(pnu.getName().isOriginalSpelling());
    assertEquals("Barleeidae", pnu.getName().getScientificName());
    assertNull(pnu.getName().getAuthorship());

    pnu = ib.interpret(SimpleName.sn(Rank.FAMILY,"Barleeidae", "[sic]"), v).get();
    assertTrue(pnu.getName().isOriginalSpelling());
    assertEquals("Barleeidae", pnu.getName().getScientificName());
    assertNull(pnu.getName().getAuthorship());

    // test various ways to supply the authorship
    pnu = ib.interpret(SimpleName.sn("Oenanthe L."), v).get();
    assertOenantheL(pnu);

    pnu = ib.interpret(SimpleName.sn("Oenanthe", "L."), v).get();
    assertOenantheL(pnu);

    pnu = interpret(null, "Oenanthe L.", null, v);
    assertOenantheL(pnu);

    pnu = interpret(null, "Oenanthe L.", "", v);
    assertOenantheL(pnu);

    pnu = interpret(null, "Oenanthe L.", "L.", v);
    assertOenantheL(pnu);

    pnu = interpret(null, "Oenanthe", "L.", v);
    assertOenantheL(pnu);

    // genus given wrongly - but corrected
    pnu = interpret("genus", "Picea", "Mill.", null,
      null, "Abies", null, null, null, v
    );
    n = pnu.getName();
    assertEquals("Abies", n.getScientificName());
    assertEquals("Abies", n.getUninomial());
    assertEquals("Mill.", n.getAuthorship());
    assertNull(n.getGenus());
    assertNull(n.getSpecificEpithet());
    assertEquals(List.of("Mill."), n.getCombinationAuthorship().getAuthors());
    assertNull("1881", n.getCombinationAuthorship().getYear());
    assertTrue(n.getBasionymAuthorship().isEmpty());
    assertTrue(v.contains(Issue.UNINOMIAL_FIELD_MISPLACED));

    // other
    pnu = interpret(null, "Cerastium ligusticum subsp. granulatum", "(Huter et al.) P. D. Sell & Whitehead",
      null, null, "tpl:234567", v);
    assertNull(pnu.getTaxonomicNote());
    n = pnu.getName();
    assertEquals("Cerastium ligusticum subsp. granulatum", n.getScientificName());
    assertEquals("(Huter et al.) P. D. Sell & Whitehead", n.getAuthorship());
    assertNull(n.getNomenclaturalNote());
    assertEquals(Rank.SUBSPECIES, n.getRank());
    assertNull(n.getUninomial());
    assertEquals("Cerastium", n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertEquals("ligusticum", n.getSpecificEpithet());
    assertEquals("granulatum", n.getInfraspecificEpithet());
    assertEquals("(Huter et al.) P. D. Sell & Whitehead", n.getAuthorship());
    assertNull(n.getBasionymAuthorship().getYear());
    assertEquals("Huter", n.getBasionymAuthorship().getAuthors().get(0));
    assertEquals("al.", n.getBasionymAuthorship().getAuthors().get(1));
    assertEquals("P.D.Sell", n.getCombinationAuthorship().getAuthors().get(0));
    assertEquals("Whitehead", n.getCombinationAuthorship().getAuthors().get(1));
    assertEquals(List.of(new Identifier("tpl", "234567")), n.getIdentifier());

    pnu = interpret("species", "Picea arlba", "Mill. and Desbrochers de Loges, 1881", null,
      null, "Abies", null, "alba", null, v
    );
    n = pnu.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // if atoms are given they take precedence over the full name
    pnu = interpret("species", "Picea arlba Mill. 2121", "",null,
      null, "Abies", null, "alba", null, v
    );
    n = pnu.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertNull(n.getAuthorship());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertNull(n.getCombinationAuthorship().getYear());

    // if no authorship is given it needs to be rebuild
    pnu = interpret("species", "Abies alba Mill. and Desbrochers de Loges, 1881", "", "",
      "", "", null, null, null, v);
    n = pnu.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // if no authorahip is given it needs to be rebuild
    pnu = interpret("species", "Abies alba Mill. and Desbrochers de Loges, 1881", "",null,
      "", "", null, null, null, v);
    n = pnu.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // exclude taxon notes from authorship
    pnu = interpret("species", "Abies alba Mill. and Desbrochers de Loges, 1881 sensu Döring 1999", "",null,
      null, "", null, null, null, v);
    assertEquals("sensu Döring 1999", pnu.getTaxonomicNote());
    n = pnu.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    pnu = interpret("species", "Abies alba", "Mill. and Desbrochers de Loges, 1881 sensu Döring 1999", null,
      null, "", null, null, null, v);
    assertEquals("sensu Döring 1999", pnu.getTaxonomicNote());
    n = pnu.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    pnu = interpret("family", "", "Miller", null,
      "Asteraceae", "", null, null, null, v);
    assertNull(pnu.getTaxonomicNote());
    n = pnu.getName();
    assertEquals("Asteraceae", n.getScientificName());
    assertEquals("Asteraceae", n.getUninomial());
    assertEquals(Rank.FAMILY, n.getRank());
    assertNull(n.getGenus());
    assertNull(n.getSpecificEpithet());
    assertEquals("Miller", n.getAuthorship());
    assertEquals(List.of("Miller"), n.getCombinationAuthorship().getAuthors());
    assertNull(n.getCombinationAuthorship().getYear());

    // https://github.com/CatalogueOfLife/backend/issues/788
    pnu = interpret("species", "Lutzomyia (Helcocyrtomyia) osornoi", "(Ristorcelli & Van ty, 1941)", null,
      null, "Lutzomyia", "Helcocyrtomyia", "osornoi", null, v);
    assertNull(pnu.getTaxonomicNote());
    n = pnu.getName();
    assertEquals("Lutzomyia (Helcocyrtomyia) osornoi", n.getScientificName());
    assertNull(n.getUninomial());
    assertEquals(Rank.SPECIES, n.getRank());
    assertEquals("Lutzomyia", n.getGenus());
    assertEquals("Helcocyrtomyia", n.getInfragenericEpithet());
    assertEquals("osornoi", n.getSpecificEpithet());
    assertEquals("(Ristorcelli & Van ty, 1941)", n.getAuthorship());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertEquals("1941", n.getBasionymAuthorship().getYear());
    assertEquals("Ristorcelli", n.getBasionymAuthorship().getAuthors().get(0));
    assertEquals("Van ty", n.getBasionymAuthorship().getAuthors().get(1));
    assertTrue(n.getBasionymAuthorship().getExAuthors().isEmpty());

    // https://github.com/CatalogueOfLife/backend/issues/788
    pnu = interpret("superfamily", "Eucnidoideae ined.", "ined.", v);
    assertNull(pnu.getTaxonomicNote());
    n = pnu.getName();
    assertEquals("Eucnidoideae", n.getScientificName());
    assertEquals("ined.", n.getAuthorship());
    assertEquals("ined.", n.getNomenclaturalNote());
    assertEquals(Rank.SUPERFAMILY, n.getRank());
    assertEquals("Eucnidoideae", n.getUninomial());
    assertNull(n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertNull(n.getSpecificEpithet());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertTrue(n.getBasionymAuthorship().isEmpty());

    // https://github.com/CatalogueOfLife/backend/issues/788
    pnu = interpret(null, "Cerastium ligusticum subsp. granulatum", "(Huter et al.) P. D. Sell & Whitehead", v);
    assertNull(pnu.getTaxonomicNote());
    n = pnu.getName();
    assertEquals("Cerastium ligusticum subsp. granulatum", n.getScientificName());
    assertEquals("(Huter et al.) P. D. Sell & Whitehead", n.getAuthorship());
    assertNull(n.getNomenclaturalNote());
    assertEquals(Rank.SUBSPECIES, n.getRank());
    assertNull(n.getUninomial());
    assertEquals("Cerastium", n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertEquals("ligusticum", n.getSpecificEpithet());
    assertEquals("granulatum", n.getInfraspecificEpithet());
    assertEquals("(Huter et al.) P. D. Sell & Whitehead", n.getAuthorship());
    assertNull(n.getBasionymAuthorship().getYear());
    assertEquals("Huter", n.getBasionymAuthorship().getAuthors().get(0));
    assertEquals("al.", n.getBasionymAuthorship().getAuthors().get(1));
    assertEquals("P.D.Sell", n.getCombinationAuthorship().getAuthors().get(0));
    assertEquals("Whitehead", n.getCombinationAuthorship().getAuthors().get(1));

    // Odonata INCONSISTENT_AUTHORSHIP
    v = new VerbatimRecord();
    pnu = interpret("species", "Boyeria vinosa (Say, 1840)", "(Say, 1840)",null,
      null, "Boyeria", null, "vinosa", null, v);
    assertNull(pnu.getTaxonomicNote());
    n = pnu.getName();
    assertFalse(v.hasIssues());

    // explicit unranked should stay: https://github.com/CatalogueOfLife/backend/issues/1136
    pnu = interpret("no rank", "cellular organisms", null, v);
    n = pnu.getName();
    assertEquals("Cellular organisms", n.getScientificName());
    assertEquals(Rank.UNRANKED, n.getRank());
    assertEquals("Cellular", n.getGenus());
    assertEquals("organisms", n.getSpecificEpithet());
    assertNull(n.getAuthorship());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertNull(n.getCombinationAuthorship().getYear());

    // daggers should be removed from name parts, not just entire names
    // https://github.com/CatalogueOfLife/data/issues/417
    pnu = interpret("species", null, "Hamilton, 1990",null,
      null, "Acixiites†", null, "costalis", null, null,
      null, null, null, "original combination, valid: Yes", v);
    n = pnu.getName();
    assertEquals("Acixiites costalis", n.getScientificName());
    assertEquals("Acixiites", n.getGenus());
    assertEquals("costalis", n.getSpecificEpithet());
    assertEquals("Hamilton, 1990", n.getAuthorship());
    assertEquals(Authorship.yearAuthors("1990", "Hamilton"), n.getCombinationAuthorship());
    assertTrue(n.getBasionymAuthorship().isEmpty());
    assertTrue(pnu.isExtinct());

    pnu = interpret("species", null, "Hamilton, 1990",null,
      null, "Acixiites", null, "costalis †", null, v);
    n = pnu.getName();
    assertEquals("Acixiites costalis", n.getScientificName());
    assertEquals("Acixiites", n.getGenus());
    assertEquals("costalis", n.getSpecificEpithet());
    assertEquals("Hamilton, 1990", n.getAuthorship());
    assertEquals(Authorship.yearAuthors("1990", "Hamilton"), n.getCombinationAuthorship());
    assertTrue(n.getBasionymAuthorship().isEmpty());
    assertTrue(pnu.isExtinct());

    pnu = interpret(null, "Leucophyta R. Br.", null, null,
      null, null, null, null, null, null,
      null, null,"botanical", null, v);
    n = pnu.getName();
    assertEquals("Leucophyta", n.getScientificName());
    assertEquals("R.Br.", n.getAuthorship());
    assertEquals("Leucophyta", n.getUninomial());
    assertNull(n.getGenus());
    assertNull(n.getSpecificEpithet());
    assertEquals(Rank.UNRANKED, n.getRank());

    // https://github.com/CatalogueOfLife/testing/issues/195
    ib.settings.enable(Setting.EPITHET_ADD_HYPHEN);
    pnu = interpret(null, null, null, null,
      null,"Cosmopterix", null, "sancti vincentii", null, null,
      null, null,"botanical", null,v);
    n = pnu.getName();
    assertEquals("Cosmopterix sancti-vincentii", n.getScientificName());
    assertNull(n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cosmopterix", n.getGenus());
    assertEquals("sancti-vincentii", n.getSpecificEpithet());

    // https://github.com/CatalogueOfLife/data/issues/431
    ib.settings.disable(Setting.EPITHET_ADD_HYPHEN);
    pnu = interpret(null, "Asplenium × mitsutae Viane & Reichst.", "Viane & Reichst.", null,
      null, "Asplenium", null, "× mitsutae", null, null,
      null, null, "botanical", null, v);
    n = pnu.getName();
    assertEquals("Asplenium × mitsutae", n.getScientificName());
    assertEquals("Viane & Reichst.", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Asplenium", n.getGenus());
    assertEquals("mitsutae", n.getSpecificEpithet());
    assertEquals(NamePart.SPECIFIC, n.getNotho());

    pnu = interpret("Species", "Cambarus Uhleri", "Faxon, 1884", "1884",
      null, "Cambarus", null, "Uhleri", null, null,
      null, null, "ICZN", null, v
    );
    n = pnu.getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertNull(n.isOriginalSpelling());
    assertNull(n.getNotho());

    // doubtful genus
    pnu = interpret(null, "[Cambarus] uhleri", "Faxon, 1884", null,
      null, null, null, null, null, v
    );
    n = pnu.getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertTrue(v.contains(Issue.DOUBTFUL_NAME));
    assertNull(n.getNotho());

    // atomised
    pnu = interpret(null, "[Cambarus] uhleri", "Faxon, 1884", null,
      null, "[Cambarus]", null, "uhleri", null, null,
      null, null, "ICZN", null, v
    );
    n = pnu.getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertTrue(v.contains(Issue.DOUBTFUL_NAME));
    assertNull(n.getNotho());

    // [sic] and corrig.
    // fully parsed
    pnu = interpret(null, "Cambarus uhleri [sic]", "Faxon, 1884", v);
    n = pnu.getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertTrue(n.isOriginalSpelling());
    assertNull(n.getNotho());

    // atoms
    pnu = interpret(null, "Cambarus uhleri [sic]", "Faxon, 1884", null,
      null, "Cambarus", null, "uhleri", null, v
    );
    n = pnu.getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertNull(n.isOriginalSpelling()); // not present in atoms
    assertNull(n.getNotho());

    pnu = interpret(null, "Cambarus uhleri [sic]", "Faxon, 1884", null,
      null, "Cambarus", null, "uhleri", null,
      null,null,"true",null,null, v
    );
    n = pnu.getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertTrue(n.isOriginalSpelling());
    assertNull(n.getNotho());

    pnu = interpret(null, "Cambarus uhleri", "Faxon, 1884", null,
      null, null, null, null, null,
      null,null,"true",null,null, v
    );
    n = pnu.getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertTrue(n.isOriginalSpelling());
    assertNull(n.getNotho());

    // various degree of author atomisations for: Abies alba (Bring. ex DC) Miller & TC Jordan, 1849
    pnu = interpret("species", "Picea", "(Bring. ex DC) Miller & TC Jordan, 1849", null,
      null, "Abies", null, "alba", null,null,
      null, null, null, null, null, null,
      null, null, null, null, v
    );
    assertAbiesAlba(pnu, v);

    pnu = interpret("species", null, null, null,
      null, "Abies", null, "alba", null, null,
      "Miller|TC Jordan", null, "1849", "DC", "Bring.", null,
      null, null, null, null, v
    );
    assertAbiesAlba(pnu, v);

    pnu = interpret("species", null, null, null,
      null, "Abies", null, "alba", null, null,
      "Miller | TC  Jordan", null, "1849", "DC", "Bring.", null,
      null, null, null, null, v
    );
    assertAbiesAlba(pnu, v);

    // https://github.com/CatalogueOfLife/backend/issues/1059
    pnu = interpret("subspecies", "Malaraeus arvicolae furkotensis", "Rosicky, 1955", "1955",
      null, "Malaraeus", null, "arvicolae", "furkotensis", null,
      null, null, null, null, null, null,
      null, null, "zoological", null, v
    );
    n = pnu.getName();
    assertEquals("Malaraeus arvicolae furkotensis", n.getScientificName());
    assertEquals("Malaraeus", n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertEquals("arvicolae", n.getSpecificEpithet());
    assertEquals("furkotensis", n.getInfraspecificEpithet());
    assertEquals("Rosicky, 1955", n.getAuthorship());
    assertEquals(List.of("Rosicky"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1955", n.getCombinationAuthorship().getYear());
    assertEquals(List.of(), n.getBasionymAuthorship().getAuthors());
    assertEquals(List.of(), n.getBasionymAuthorship().getExAuthors());
    assertNull(n.getBasionymAuthorship().getYear());
    assertFalse(v.hasIssues());
  }

  /**
   * Abies alba (Bring. ex DC) Miller & TC Jordan, 1849
   */
  void assertAbiesAlba(ParsedNameUsage pnu, IssueContainer v) {
    var n = pnu.getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertEquals("alba", n.getSpecificEpithet());
    assertNull(n.getInfraspecificEpithet());

    assertEquals("(Bring. ex DC) Miller & TC Jordan, 1849", n.getAuthorship());
    assertEquals(List.of("Miller", "TC Jordan"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1849", n.getCombinationAuthorship().getYear());
    assertEquals(List.of("DC"), n.getBasionymAuthorship().getAuthors());
    assertEquals(List.of("Bring."), n.getBasionymAuthorship().getExAuthors());
    assertNull(n.getBasionymAuthorship().getYear());

    assertFalse(v.hasIssues());
  }
}
