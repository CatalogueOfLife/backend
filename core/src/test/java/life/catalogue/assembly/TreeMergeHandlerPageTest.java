package life.catalogue.assembly;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.InfoGroup;

import java.util.EnumSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TreeMergeHandlerPageTest {

  private static Name name(String page, String link) {
    Name n = new Name();
    n.setPublishedInPage(page);
    n.setPublishedInPageLink(link);
    return n;
  }

  private static Set<InfoGroup> usePageOf(Name src, Name n) {
    Set<InfoGroup> upd = EnumSet.noneOf(InfoGroup.class);
    TreeMergeHandler.usePageOf(src, n, upd);
    return upd;
  }

  @Test
  public void creditsWhatChanged() {
    Name n = name(null, null);
    assertEquals(Set.of(InfoGroup.PUBLISHED_IN_PAGE, InfoGroup.PUBLISHED_IN_PAGE_LINK), usePageOf(name("12", "http://a"), n));
    assertEquals("12", n.getPublishedInPage());
    assertEquals("http://a", n.getPublishedInPageLink());

    n = name("12", null);
    assertEquals(Set.of(InfoGroup.PUBLISHED_IN_PAGE_LINK), usePageOf(name("12", "http://a"), n));

    n = name("12", "http://a");
    assertEquals(Set.of(), usePageOf(name("12", "http://a"), n));
  }

  @Test
  public void sourceWithoutPageKeepsExisting() {
    Name n = name("12", "http://a");
    assertEquals(Set.of(), usePageOf(name(null, null), n));
    assertEquals("12", n.getPublishedInPage());
    assertEquals("http://a", n.getPublishedInPageLink());
  }

  @Test
  public void sourcePageReplacesExisting() {
    Name n = name("12", "http://a");
    assertEquals(Set.of(InfoGroup.PUBLISHED_IN_PAGE), usePageOf(name("40", null), n));
    assertEquals("40", n.getPublishedInPage());
    assertEquals("http://a", n.getPublishedInPageLink());
  }
}
