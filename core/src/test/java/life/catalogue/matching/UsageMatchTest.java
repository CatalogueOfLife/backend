package life.catalogue.matching;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class UsageMatchTest {

  @Test
  public void match() {
    SimpleNameClassified<SimpleNameCached> u = new SimpleNameClassified<>();
    List<SimpleNameClassified<SimpleNameCached>> alt = new ArrayList<>();
    var m = UsageMatch.match(u,100, alt);

    u.setId("123");
    m = UsageMatch.match(u,100, alt);
    assertEquals(0, m.alternatives.size());

    alt.add(new SimpleNameClassified<>(SimpleName.sn("xyz", Rank.SPECIES, "Abies falta", null)));
    m = UsageMatch.match(u,100, alt);
    assertEquals(1, m.alternatives.size());

    alt.add(new SimpleNameClassified<>(SimpleName.sn("123", Rank.SPECIES, "Abies alba", null)));
    alt.add(new SimpleNameClassified<>(SimpleName.sn("124", Rank.SPECIES, "Abies bumba", null)));
    alt.add(new SimpleNameClassified<>(SimpleName.sn("125", Rank.SPECIES, "Abies cumba", null)));
    m = UsageMatch.match(u,100, alt);
    assertEquals(3, m.alternatives.size());
  }
}