package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Setting;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.Rank;

import java.util.List;
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

  @Test
  public void interpretName() throws Exception {
    VerbatimRecord v = new VerbatimRecord();
    Optional<ParsedNameUsage> pnu;
    Name n;

    pnu = ib.interpret("1", null, "Cerastium ligusticum subsp. granulatum", "(Huter et al.) P. D. Sell & Whitehead",
      null, null, null, null, null, null, null, null, null, null, null, "tpl:234567", v);
    assertNull(pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
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

    pnu = ib.interpret("1", "species", "Picea arlba", "Mill. and Desbrochers de Loges, 1881",null,
      null, "Abies", null, "alba", null, null, null, null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // if atoms are given they take precedence over the full name
    pnu = ib.interpret("1", "species", "Picea arlba Mill. 2121", "",null,
      null, "Abies", null, "alba", null, null, null, null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertNull(n.getAuthorship());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertNull(n.getCombinationAuthorship().getYear());

    // if no authorahip is given it needs to be rebuild
    pnu = ib.interpret("1", "species", "Abies alba Mill. and Desbrochers de Loges, 1881", "",null,
      "", "", null, null, null, null, null, null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // if no authorahip is given it needs to be rebuild
    pnu = ib.interpret("1", "species", "Abies alba Mill. and Desbrochers de Loges, 1881", "",null,
      "", "", null, null, null, null, null, null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    // exclude taxon notes from authorship
    pnu = ib.interpret("1", "species", "Abies alba Mill. and Desbrochers de Loges, 1881 sensu Döring 1999", "",null,
      null, "", null, null, null, null, null, null, null, null, null, v);
    assertEquals("sensu Döring 1999", pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    pnu = ib.interpret("1", "species", "Abies alba", "Mill. and Desbrochers de Loges, 1881 sensu Döring 1999",null,
      null, "", null, null, null, null, null, null, null, null, null, v);
    assertEquals("sensu Döring 1999", pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Abies", n.getGenus());
    assertEquals("alba", n.getSpecificEpithet());
    assertEquals("Mill. & Desbrochers de Loges, 1881", n.getAuthorship());
    assertEquals(List.of("Mill.", "Desbrochers de Loges"), n.getCombinationAuthorship().getAuthors());
    assertEquals("1881", n.getCombinationAuthorship().getYear());

    pnu = ib.interpret("1", "family", "", "Miller",null,
      "Asteraceae", "", null, null, null, null, null, null, null, null, null, v);
    assertNull(pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
    assertEquals("Asteraceae", n.getScientificName());
    assertEquals("Asteraceae", n.getUninomial());
    assertEquals(Rank.FAMILY, n.getRank());
    assertNull(n.getGenus());
    assertNull(n.getSpecificEpithet());
    assertEquals("Miller", n.getAuthorship());
    assertEquals(List.of("Miller"), n.getCombinationAuthorship().getAuthors());
    assertNull(n.getCombinationAuthorship().getYear());

    // https://github.com/CatalogueOfLife/backend/issues/788
    pnu = ib.interpret("CIP-82", "species", "Lutzomyia (Helcocyrtomyia) osornoi", "(Ristorcelli & Van ty, 1941)",null,
      null, "Lutzomyia", "Helcocyrtomyia", "osornoi", null, null, null, null, null, null, null, v);
    assertNull(pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
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
    pnu = ib.interpret("1", "superfamily", "Eucnidoideae ined.", "ined.",null,
      null, null, null, null, null, null, null, null, null, null, null, v);
    assertNull(pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
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
    pnu = ib.interpret("1", null, "Cerastium ligusticum subsp. granulatum", "(Huter et al.) P. D. Sell & Whitehead",null,
      null, null, null, null, null, null, null, null, null, null, null, v);
    assertNull(pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
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
    pnu = ib.interpret("957", "species", "Boyeria vinosa (Say, 1840)", "(Say, 1840)",null,
      null, "Boyeria", null, "vinosa", null, null, null, null, null, null, null, v);
    assertNull(pnu.get().getTaxonomicNote());
    n = pnu.get().getName();
    assertFalse(v.hasIssues());

    // explicit unranked should stay: https://github.com/CatalogueOfLife/backend/issues/1136
    pnu = ib.interpret("1", "no rank", "cellular organisms", null,null,
      null, null, null, null, null, null, null, null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Cellular organisms", n.getScientificName());
    assertEquals(Rank.UNRANKED, n.getRank());
    assertEquals("Cellular", n.getGenus());
    assertEquals("organisms", n.getSpecificEpithet());
    assertNull(n.getAuthorship());
    assertTrue(n.getCombinationAuthorship().isEmpty());
    assertNull(n.getCombinationAuthorship().getYear());

    // daggers should be removed from name parts, not just entire names
    // https://github.com/CatalogueOfLife/data/issues/417
    pnu = ib.interpret("1", "species", null, "Hamilton, 1990",null,
      null, "Acixiites†", null, "costalis", null, null, null, "original combination, valid: Yes", null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Acixiites costalis", n.getScientificName());
    assertEquals("Acixiites", n.getGenus());
    assertEquals("costalis", n.getSpecificEpithet());
    assertEquals("Hamilton, 1990", n.getAuthorship());
    assertEquals(Authorship.yearAuthors("1990", "Hamilton"), n.getCombinationAuthorship());
    assertTrue(n.getBasionymAuthorship().isEmpty());

    pnu = ib.interpret("1", "species", null, "Hamilton, 1990",null,
      null, "Acixiites", null, "costalis †", null, null, null, null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Acixiites costalis", n.getScientificName());
    assertEquals("Acixiites", n.getGenus());
    assertEquals("costalis", n.getSpecificEpithet());
    assertEquals("Hamilton, 1990", n.getAuthorship());
    assertEquals(Authorship.yearAuthors("1990", "Hamilton"), n.getCombinationAuthorship());
    assertTrue(n.getBasionymAuthorship().isEmpty());

    pnu = ib.interpret("1", null, "Leucophyta R. Br.", null, null,null, null, null, null, null, null, "botanical", null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Leucophyta", n.getScientificName());
    assertEquals("R.Br.", n.getAuthorship());
    assertEquals("Leucophyta", n.getUninomial());
    assertNull(n.getGenus());
    assertNull(n.getSpecificEpithet());
    assertEquals(Rank.UNRANKED, n.getRank());

    // https://github.com/CatalogueOfLife/testing/issues/195
    ib.settings.enable(Setting.EPITHET_ADD_HYPHEN);
    pnu = ib.interpret("1", null, null, null, null, null,"Cosmopterix", null, "sancti vincentii", null, null, "botanical", null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Cosmopterix sancti-vincentii", n.getScientificName());
    assertNull(n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cosmopterix", n.getGenus());
    assertEquals("sancti-vincentii", n.getSpecificEpithet());

    // https://github.com/CatalogueOfLife/data/issues/431
    ib.settings.disable(Setting.EPITHET_ADD_HYPHEN);
    pnu = ib.interpret("1", null, "Asplenium × mitsutae Viane & Reichst.", "Viane & Reichst.", null,null, "Asplenium", null, "× mitsutae", null, null, "botanical", null, null, null, null, v);
    n = pnu.get().getName();
    assertEquals("Asplenium × mitsutae", n.getScientificName());
    assertEquals("Viane & Reichst.", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Asplenium", n.getGenus());
    assertEquals("mitsutae", n.getSpecificEpithet());
    assertEquals(NamePart.SPECIFIC, n.getNotho());

    pnu = ib.interpret("urn:lsid:marinespecies.org:taxname:887642", "Species", "Cambarus Uhleri", "Faxon, 1884", "1884",
      null, "Cambarus", null, "Uhleri", null, null,
      "ICZN", null, null, null, null, v
    );
    n = pnu.get().getName();
    assertEquals("Cambarus uhleri", n.getScientificName());
    assertEquals("Faxon, 1884", n.getAuthorship());
    assertNull(n.getUninomial());
    assertEquals("Cambarus", n.getGenus());
    assertEquals("uhleri", n.getSpecificEpithet());
    assertEquals(Rank.SPECIES, n.getRank());
    assertNull(n.getNotho());
  }

}
