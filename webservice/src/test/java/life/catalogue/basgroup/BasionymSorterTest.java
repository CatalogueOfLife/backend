package life.catalogue.basgroup;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Another author comparator test that runs over files of names taken from the real GBIF backbone.
 * Each file contains a group of names that share the same terminal epithet within a family.
 * See http://dev.gbif.org/issues/browse/POR-398 for more.
 */
public class BasionymSorterTest {
  private final static AtomicInteger PRIO = new AtomicInteger(1);
  private final BasionymSorter<Name> sorter = new BasionymSorter<>(
    new AuthorComparator(AuthorshipNormalizer.INSTANCE), Name::getSectorKey
  );

  private static Name parse(String x) {
    try {
      return NameParser.PARSER.parse(x, null, null, VerbatimRecord.VOID).get().getName();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("parser got interrupted");
    }
  }

  private static Name parse(String x, Rank rank) throws InterruptedException {
    var n = NameParser.PARSER.parse(x, rank, null, VerbatimRecord.VOID).get().getName();
    n.setSectorKey(PRIO.getAndIncrement());
    return n;
  }

  /**
   * Uses Name.sectorKey as the priority supplier!
   * for tests only !!!
   */
  Collection<HomotypicGroup<Name>> groupBasionyms(NomCode code, String epithet, List<Name> names) {
    return sorter.groupBasionyms(code, epithet, names, Functions.identity(), n->{});
  }

  @Before
  public void init() {
    PRIO.set(1);
  }

  /**
   * priority of names corresponds to ordering, first name has highest prio
   */
  private List<Name> names(String... names) throws Exception {
    return Arrays.stream(names)
        .map(n -> {
          var pn = BasionymSorterTest.parse(n);
          pn.setSectorKey(PRIO.getAndIncrement());
          return pn;
        })
        .collect(Collectors.toList());
  }

  @Test
  public void testGroupPlantBasionyms() throws Exception {
    List<Name> names = names(
        "Gymnolomia microcephala var. abbreviata (B.L.Rob. & Greenm.) B.L.Rob. & Greenm.",
        "Leucheria abbreviata (Bertero) Steud.",
        "Centaurea phrygia subsp. abbreviata (K. Koch) Dostál",
        "Centaurea abbreviata (K.Koch) Hand.-Mazz.",
        "Jacea abbreviata (K.Koch) Soják",
        "Artemisia abbreviata (Krasch. ex Korobkov) Krasnob.",
        "Artemisia lagopus subsp. abbreviata Krasch. ex Korobkov",
        "Bigelowia leiosperma var. abbreviata M.E.Jones",
        "Brickellia oblongifolia var. abbreviata A.Gray",
        "Calea abbreviata Pruski & Urbatsch",
        "Centaurea salicifolia subsp. abbreviata K. Koch",
        "Chabraea abbreviata Colla ex Bertero",
        "Chaetanthera stuebelii Hieron. var. abbreviata Cabrera",
        "Conyza abbreviata Wall.",
        "Cousinia abbreviata Tscherneva",
        "Gymnolomia patens var. abbreviata B.L.Rob. & Greenm.",
        "Gynura abbreviata F.G.Davies",
        "Jacea abbreviata subsp. abbreviata",
        "Nassauvia abbreviata Dusén",
        "Nassauvia abbreviata var. abbreviata",
        "Scorzonera latifolia var. abbreviata Lipsch.",
        "Vernonia abbreviata DC."
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(NomCode.BOTANICAL, "abbreviata", names);
    assertEquals(14, groups.size());
    for (HomotypicGroup<Name> g : groups) {
      switch (g.getAuthorship().toString()) {
        case "B.L.Rob. & Greenm.":
          assertEquals(1, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        case "Bertero":
          assertEquals(1, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        case "K.Koch":
          assertEquals(3, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        case "Krasch. ex Korobkov":
          assertEquals(1, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
      }
    }
  }

  /**
   * Here we have a real case from the Asteraceae where 2 different authors with the same surname exist.
   * A.Nelson and E.E.Nelson must be kept separate!
   * http://kiki.huh.harvard.edu/databases/botanist_search.php?botanistid=628
   * http://kiki.huh.harvard.edu/databases/botanist_search.php?botanistid=519
   */
  @Test
  public void testGroupPlantBasionyms2() throws Exception {
    List<Name> names = names(
        "Triniteurybia aberrans (A. Nelson) Brouillet, Urbatsch & R.P. Roberts",
        "Haplopappus aberrans (A.Nelson) H.M.Hall",
        "Sideranthus aberrans (A.Nelson) Rydb.",
        "Tonestus aberrans (A.Nelson) G.L.Nesom & D.R.Morgan",
        "Hysterionica aberrans (Cabrera) Cabrera",
        "Antennaria luzuloides ssp. aberrans (E.E. Nelson) Bayer & Stebbins",
        "Logfia aberrans (Wagenitz) Anderb.",
        "Antennaria argentea subsp. aberrans",
        "Filago aberrans Wagenitz",
        "Hysterionica aberrans var. aberrans",
        "Hysterionica bakeri var. aberrans Cabrera",
        "Macronema aberrans A.Nelson",
        "Senecio aberrans Greenm.",
        "Taraxacum aberrans Hagend. & al."
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "aberrans", names);
    assertEquals(6, groups.size());
    for (HomotypicGroup<Name> g : groups) {
      switch (g.getAuthorship().toString()) {
        case "A.Nelson":
          assertEquals(5, g.size());
          assertNotNull(g.getBasionym());
          assertFalse(g.getRecombinations().isEmpty());
          break;
        case "Cabrera":
          assertEquals(2, g.size());
          assertNotNull(g.getBasionym());
          break;
        case "E.E.Nelson":
          assertEquals(1, g.size());
          assertNull(g.getBasionym());
          break;
        case "Wagenitz":
          assertEquals(2, g.size());
          assertNotNull(g.getBasionym());
          break;
        case "Greenm.":
          assertEquals(1, g.size());
          assertNotNull(g.getBasionym());
          break;
        case "Hagend. et al.":
          assertEquals(1, g.size());
          assertNotNull(g.getBasionym());
          break;
        default:
          fail("Unknown basionym group " + g.getAuthorship());
      }
    }
  }

  @Test
  public void testGroupPlantBasionyms3() throws Exception {
    List<Name> names = names(
        "Negundo aceroides subsp. violaceus (G.Kirchn.) W.A.Weber",
        "Negundo aceroides subsp. violaceus (Kirchner) W.A. Weber",

        "Negundo aceroides subsp. violaceum (Booth ex G.Kirchn.) Holub",
        "Negundo aceroides subsp. violaceum (Booth ex Kirchner) Holub",

        "Negundo aceroides var. violaceum G.Kirchn. in Petzold & G.Kirchn.",
        "Acer violaceum (Kirchner) Simonkai",
        "Acer negundo var. violaceum (G. Kirchn.) H. Jaeger"
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "violaceum", names);
    assertEquals(1, groups.size());
    HomotypicGroup<Name> g = groups.iterator().next();
    assertFalse(g.getRecombinations().isEmpty());
    assertEquals(6, g.getRecombinations().size());
    assertNotNull(g.getBasionym());
    assertEquals("G.Kirchn.", g.getBasionym().getAuthorship());
  }

  @Test
  public void testGroupWithDifferentInitials() throws Exception {
    List<Name> names = names(
        "Negundo aceroides subsp. violaceum (Booth ex G.Kirchn.) Holub",
        "Negundo aceroides subsp. violaceum (Booth ex Kirchn.) Holub",

        "Negundo aceroides var. violaceum G.Kirchn. in Petzold & G.Kirchn.",
        "Acer violaceum (T.Kirchn.) Simonkai",
        "Acer negundo var. violaceum (G. Kirchn.) H. Jaeger"
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "violaceum", names);
    assertEquals(2, groups.size());
    for (HomotypicGroup<Name> g : groups) {
      assertFalse(g.getRecombinations().isEmpty());
      switch (g.getAuthorship().toString()) {
        case "Booth ex G.Kirchn.":
          // Kirchn. is the abbreviation for Emil Otto Oskar Kirchner
          assertEquals(3, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        case "T.Kirchn.":
          // author comparison has to be very strict and must treat different initials as relevant
          assertEquals(1, g.getRecombinations().size());
          assertNull(g.getBasionym());
          break;
        default:
          fail("Unknown basionym group " + g.getRecombinations().get(0));
      }
    }
  }

  @Test
  public void testGroupAuthorTeams() throws Exception {
    List<Name> names = names(
        "Negundo aceroides var. californicum (Torr. & A.Gray) Sarg.",
        "Acer negundo var. californicum (Torr. & Gray) Sarg.",
        "Acer californicum Torr et Gray"
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "californicum", names);
    assertEquals(1, groups.size());
    HomotypicGroup<Name> g = groups.iterator().next();
    assertEquals(2, g.getRecombinations().size());
    assertEquals("Acer californicum", g.getBasionym().getScientificName());
    assertEquals("Torr & Gray", g.getBasionym().getCombinationAuthorship().toString());
  }

  @Test
  public void testAtrocincta() throws Exception {
    List<Name> names = new ArrayList<>();

    names.add(parse("Anthophora atrocincta Lepeletier, 1841", Rank.SPECIES));
    names.add(parse("Amegilla atrocincta (Lepeletier)", Rank.SPECIES));

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "atrocincta", names);
    assertEquals(1, groups.size());
    HomotypicGroup<Name> g = groups.iterator().next();
    assertEquals(1, g.getRecombinations().size());
    assertEquals("Anthophora atrocincta", g.getBasionym().getScientificName());
    assertEquals("Lepeletier, 1841", g.getBasionym().getCombinationAuthorship().toString());
  }

  @Test
  public void testPlumipes() throws Exception {
    List<Name> names = new ArrayList<>();

    names.add(parse("Anthophora plumipes (Fabricius)", Rank.SPECIES));
    names.add(parse("Apis plumipes Fabricius, 1781", Rank.SPECIES));
    names.add(parse("Centris plumipes (Fabricius)", Rank.SPECIES));

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "plumipes", names);
    assertEquals(1, groups.size());
    HomotypicGroup<Name> g = groups.iterator().next();
    assertEquals(2, g.getRecombinations().size());
    assertEquals("Apis plumipes", g.getBasionym().getScientificName());
    assertEquals("Fabricius, 1781", g.getBasionym().getCombinationAuthorship().toString());
  }

  /**
   * Test what happens if a group contains 2 or more basionyms.
   */
  @Test
  public void testMultipleBasionyms() throws Exception {
    List<Name> names = names(
        "Negundo violaceum G.Kirchn.",
        "Negundo aceroides var. violaceum G.Kirchn. in Petzold & G.Kirchn.",
        "Acer violaceum (G Kirchn.) Simonkai",
        "Acer negundo var. violaceum (G. Kirchn.) H. Jaeger"
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "violaceum", names);
    assertEquals(1, groups.size());
    var g = groups.iterator().next();
    assertEquals(4, g.size());
    // the highest priority name wins - the first in our tests
    assertEquals("Negundo violaceum G.Kirchn.", g.getBasionym().getLabel());
  }

  @Test
  public void testGroupAnimalBasionyms() throws Exception {
    List<Name> names = names(
        "Microtus parvulus (A. H. Howell, 1916)",
        "Microtus pinetorum parvulus (A. H. Howell, 1916)",
        "Pitymys parvulus A. H. Howell, 1916"
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "parvulus", names);
    assertEquals(1, groups.size());
    HomotypicGroup<Name> g = groups.iterator().next();
    assertEquals(2, g.getRecombinations().size());
    assertNotNull(g.getBasionym());
    assertEquals("A.H.Howell, 1916", g.getBasionym().getCombinationAuthorship().toString());
    assertEquals("1916", g.getBasionym().getCombinationAuthorship().getYear());
  }

  @Test
  public void testGroupAnimalBasionyms2() throws Exception {
    List<Name> names = names(
        "Heliodoxa rubinoides aequatorialis (Gould, 1860)",
        "Androdon aequatorialis Gould, 1863",
        "Clementoron aequatorialis Gould, 1864",
        "Campylopterus largipennis aequatorialis Gould, 1861",
        // this one has a placeholder year so it matches the first recombination on top! but prefer the proper year
        "Campylopterus largipennis aequatorialis Gould, 186?",
        "Campylopterus largipennis aequatorialis Gould, 1860"
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(null, "aequatorialis", names);
    // multiple basionyms, no clear group!
    assertEquals(1, groups.size());
    HomotypicGroup<Name> bg = groups.iterator().next();
    assertEquals("aequatorialis", bg.getEpithet());
    assertEquals("Gould, 1860", bg.getAuthorship().toString());
    assertEquals("Androdon aequatorialis Gould, 1863", bg.getBasionym().getLabel());
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1355
   */
  @Test
  public void testExAuthors() throws Exception {
    List<Name> names = names(
      "Mimosa guaiaguilensis Desf. ex F.Dietr.",
      "Acacia guaiaguilensis (Desf. ex F.Dietr.) Desf. ex DC.",
      "Mimosa guayaquilensis Desf.",
      "Acacia guayaquilensis Desf. ex DC.",
      "Mimosa guayaquilensis Steud."
    );

    Collection<HomotypicGroup<Name>> groups = groupBasionyms(NomCode.BOTANICAL, "guaiaguilensis", names);
    assertEquals(3, groups.size());
    for (HomotypicGroup<Name> g : groups) {
      switch (g.getAuthorship().toString()) {
        case "Desf. ex F.Dietr.":
          assertEquals(3, g.size());
          assertEquals(1, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          assertNotNull(g.getBasedOn());
          break;
        case "Desf. ex DC.":
          assertEquals(1, g.size());
          assertNotNull(g.getBasionym());
          break;
        case "Steud.":
          assertEquals(1, g.size());
          assertNotNull(g.getBasionym());
          break;
        default:
          fail("Unknown basionym group " + g.getRecombinations().get(0));
      }
    }
  }

}