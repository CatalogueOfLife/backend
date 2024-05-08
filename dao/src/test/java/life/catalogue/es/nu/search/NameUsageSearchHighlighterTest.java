package life.catalogue.es.nu.search;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.search.NameUsageWrapper;

import org.gbif.nameparser.api.Authorship;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;

import org.gbif.nameparser.api.ExAuthorship;

import org.junit.Test;

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
    return nuw;
  }

  private static ExAuthorship auth(String... names) {
    var a = new ExAuthorship();
    a.setAuthors(Arrays.asList(names));
    return a;
  }

  private static NameUsageHighlighter createHighlighter(String q, SearchContent... sc) {
    NameUsageSearchRequest nsr = new NameUsageSearchRequest();
    nsr.setQ(q);
    if (sc.length == 0) {
      nsr.setContent(EnumSet.allOf(SearchContent.class));
    } else {
      nsr.setContent(new HashSet<>(Arrays.asList(sc)));
    }
    return new NameUsageHighlighter(nsr, null);
  }

  @Test
  public void test2() {
    NameUsageWrapper nuw = malusSylvestris();
    createHighlighter("sylvestris").highlight(nuw);
    assertEquals("Malus <em class='highlight'>sylvestr</em>is", nuw.getUsage().getName().getScientificName());
  }

  @Test
  public void test3() {
    NameUsageWrapper nuw = malusSylvestris();
    createHighlighter("ilvestri").highlight(nuw);
    assertEquals("Malus s<em class='highlight'>ylvestr</em>is", nuw.getUsage().getName().getScientificName());
  }

}
