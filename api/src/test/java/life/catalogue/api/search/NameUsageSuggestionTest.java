package life.catalogue.api.search;

import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameUsageSuggestionTest {

  @Test
  public void getSuggestion() {
    NameUsageSuggestion s = new NameUsageSuggestion();
    s.setUsageId("1");
    s.setMatch("Acanthocephala");
    s.setRank(Rank.PHYLUM);
    assertEquals("Acanthocephala (bare name)", s.getSuggestion());

    s.setStatus(TaxonomicStatus.ACCEPTED);
    assertEquals("Acanthocephala (phylum)", s.getSuggestion());

    s.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    assertEquals("Acanthocephala (prov. phylum)", s.getSuggestion());

    s.setContext("Animalia");
    assertEquals("Acanthocephala (prov. phylum in Animalia)", s.getSuggestion());

    s.setStatus(TaxonomicStatus.ACCEPTED);
    assertEquals("Acanthocephala (phylum in Animalia)", s.getSuggestion());

    s.setStatus(TaxonomicStatus.SYNONYM);
    assertEquals("Acanthocephala (synonym of Animalia)", s.getSuggestion());


    s.setMatch("Acanthos forte");
    s.setRank(Rank.SPECIES);
    assertEquals("Acanthos forte (synonym of Animalia)", s.getSuggestion());

    s.setStatus(TaxonomicStatus.ACCEPTED);
    assertEquals("Acanthos forte (Animalia)", s.getSuggestion());

    s.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    assertEquals("Acanthos forte (prov. species in Animalia)", s.getSuggestion());
  }
}