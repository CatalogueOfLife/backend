package life.catalogue.es.nu.search;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.gbif.nameparser.api.Authorship;
import org.junit.Test;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.VernacularName;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.search.NameUsageWrapper;
import static org.junit.Assert.assertEquals;

public class NameUsageSearchHighlighterTest {

  private static NameUsageWrapper malusSylvestris() {
    Name n = new Name();
    n.setScientificName("Malus sylvestris"); // y gets replaced with i in normalized name !
    n.setCombinationAuthorship(auth("Jimmy Malone", "Drège, JF", "Krüger"));
    n.setBasionymAuthorship(auth("Jan Van De Appelboom"));
    Taxon t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw = new NameUsageWrapper(t);
    nuw.setVernacularNames(vernaculars("De appelboom", "Der Apfelbaum", "The Apple Tree"));
    return nuw;
  }

  private static List<VernacularName> vernaculars(String... names) {
    return Arrays.stream(names).map(n -> {
      VernacularName vn = new VernacularName();
      vn.setName(n);
      return vn;
    }).collect(Collectors.toList());
  }

  private static Authorship auth(String... names) {
    Authorship a = new Authorship();
    a.setAuthors(Arrays.asList(names));
    return a;
  }

  private static NameSearchHighlighter createHighlighter(String q, SearchContent... sc) {
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.setQ(q);
    if (sc.length == 0) {
      nsr.setContent(EnumSet.allOf(SearchContent.class));
    } else {
      nsr.setContent(new HashSet<SearchContent>(Arrays.asList(sc)));
    }
    return new NameSearchHighlighter(nsr, null);
  }

  @Test
  public void test1() {
    NameUsageWrapper nuw = malusSylvestris();
    createHighlighter("APP").highlight(nuw);
    assertEquals("De <em class='highlight'>app</em>elboom", nuw.getVernacularNames().get(0).getName());
    assertEquals("Der Apfelbaum", nuw.getVernacularNames().get(1).getName());
    assertEquals("The <em class='highlight'>App</em>le Tree", nuw.getVernacularNames().get(2).getName());
    assertEquals("Jan Van De <em class='highlight'>App</em>elboom", nuw.getUsage().getName().getBasionymAuthorship().getAuthors().get(0));
  }

  @Test
  public void test2() {
    NameUsageWrapper nuw = malusSylvestris();
    createHighlighter("silvestris").highlight(nuw);
    assertEquals("Malus <em class='highlight'>sylvestris</em>", nuw.getUsage().getName().getScientificName());
  }

  @Test
  public void test3() {
    NameUsageWrapper nuw = malusSylvestris();
    createHighlighter("ilvestri").highlight(nuw);
    assertEquals("Malus s<em class='highlight'>ylvestri</em>s", nuw.getUsage().getName().getScientificName());
  }

  @Test
  public void test4() {
    NameUsageWrapper nuw = malusSylvestris();
    createHighlighter("The Apple Tree").highlight(nuw);
    assertEquals("<em class='highlight'>The Apple Tree</em>", nuw.getVernacularNames().get(2).getName());
  }

}
