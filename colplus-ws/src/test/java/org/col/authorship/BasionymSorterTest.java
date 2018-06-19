package org.col.authorship;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.col.api.model.Name;
import org.col.parser.NameParser;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Another autho comparator test that runs over files of names taken from the real GBIF backbone.
 * Each file contains a group of names that share the same terminal epithet within a family.
 * See http://dev.gbif.org/issues/browse/POR-398 for more.
 */
public class BasionymSorterTest {
  private final BasionymSorter sorter = new BasionymSorter();

  private static Name parse(String x) {
    return NameParser.PARSER.parse(x).get().getName();
  }
  private static Name parse(String x, Rank rank) {
    return NameParser.PARSER.parse(x, rank, null).get().getName();
  }

  private List<Name> names(String... names) throws Exception {
    return Arrays.stream(names)
        .map(BasionymSorterTest::parse)
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

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(4, groups.size());
    for (BasionymGroup<Name> g : groups) {
      assertFalse(g.getRecombinations().isEmpty());
      switch (g.getRecombinations().get(0).getBasionymAuthorship().toString()) {
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
        default:
          fail("Unknown basionym group " + g.getRecombinations().get(0));
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

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(4, groups.size());
    for (BasionymGroup<Name> g : groups) {
      assertFalse(g.getRecombinations().isEmpty());
      switch (g.getRecombinations().get(0).getBasionymAuthorship().toString()) {
        case "A.Nelson":
          assertEquals(4, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        case "Cabrera":
          assertEquals(1, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        case "E.E.Nelson":
          assertEquals(1, g.getRecombinations().size());
          assertNull(g.getBasionym());
          break;
        case "Wagenitz":
          assertEquals(1, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        default:
          fail("Unknown basionym group " + g.getRecombinations().get(0));
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

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(1, groups.size());
    BasionymGroup<Name> g = groups.iterator().next();
    assertFalse(g.getRecombinations().isEmpty());
    assertEquals(6, g.getRecombinations().size());
    assertNotNull(g.getBasionym());
    assertEquals("G.Kirchn.", g.getBasionym().authorshipComplete());
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

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(3, groups.size());
    for (BasionymGroup<Name> g : groups) {
      assertFalse(g.getRecombinations().isEmpty());
      switch (g.getRecombinations().get(0).getBasionymAuthorship().toString()) {
        case "Booth ex G.Kirchn.":
          assertEquals(2, g.getRecombinations().size());
          assertNotNull(g.getBasionym());
          break;
        case "T.Kirchn.":
          // author comparison has to be very strict and must treat different initials as relevant
          assertEquals(1, g.getRecombinations().size());
          assertNull(g.getBasionym());
          break;
        case "Booth ex Kirchn.":
          // Kirchn. is the abbreviation for Emil Otto Oskar Kirchner
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

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(1, groups.size());
    BasionymGroup<Name> g = groups.iterator().next();
    assertEquals(2, g.getRecombinations().size());
    assertEquals("Acer californicum", g.getBasionym().getScientificName());
    assertEquals("Torr & Gray", g.getBasionym().getCombinationAuthorship().toString());
  }

  @Test
  public void testAtrocincta() throws Exception {
    List<Name> names = Lists.newArrayList();

    names.add(parse("Anthophora atrocincta Lepeletier, 1841", Rank.SPECIES));
    names.add(parse("Amegilla atrocincta (Lepeletier)", Rank.SPECIES));

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(1, groups.size());
    BasionymGroup<Name> g = groups.iterator().next();
    assertEquals(1, g.getRecombinations().size());
    assertEquals("Anthophora atrocincta", g.getBasionym().getScientificName());
    assertEquals("Lepeletier, 1841", g.getBasionym().getCombinationAuthorship().toString());
  }

  @Test
  public void testPlumipes() throws Exception {
    List<Name> names = Lists.newArrayList();

    names.add(parse("Anthophora plumipes (Fabricius)", Rank.SPECIES));
    names.add(parse("Apis plumipes Fabricius, 1781", Rank.SPECIES));
    names.add(parse("Centris plumipes (Fabricius)", Rank.SPECIES));

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(1, groups.size());
    BasionymGroup<Name> g = groups.iterator().next();
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

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertTrue(groups.isEmpty());
  }

  @Test
  public void testGroupAnimalBasionyms() throws Exception {
    List<Name> names = names(
        "Microtus parvulus (A. H. Howell, 1916)",
        "Microtus pinetorum parvulus (A. H. Howell, 1916)",
        "Pitymys parvulus A. H. Howell, 1916"
    );

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    assertEquals(1, groups.size());
    BasionymGroup<Name> g = groups.iterator().next();
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
        // this one is 1 year apart so it matches the first recombination on top!
        "Campylopterus largipennis aequatorialis Gould, 1861"
    );

    Collection<BasionymGroup<Name>> groups = sorter.groupBasionyms(names);
    // multiple basionyms, no clear group!
    assertEquals(1, groups.size());
    BasionymGroup<Name> bg = groups.iterator().next();
    assertEquals("aequatorialis", bg.getEpithet());
    assertEquals("1861", bg.getBasionym().getCombinationAuthorship().getYear());
    assertEquals("aequatorialis", bg.getBasionym().getInfraspecificEpithet());
    assertEquals("Gould, 1860", bg.getAuthorship().toString());
  }

}