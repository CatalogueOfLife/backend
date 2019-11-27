package life.catalogue.api.model;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

public class StatusRankCounts extends HashMap<TaxonomicStatus, Map<Rank, Integer>> {
  
  public int getUsageCount() {
    return countAllStatus(entrySet().stream());
  }

  public int getTaxonCount() {
    return countAllStatus(entrySet().stream()
        .filter(e -> !e.getKey().isSynonym())
    );
  }
  
  public int getSynonymCount() {
    return countAllStatus(entrySet().stream()
        .filter(e -> e.getKey().isSynonym())
    );
  }

  private static int countAllRanks(Stream<Map.Entry<Rank, Integer>> stream) {
    return stream
        .mapToInt(Entry::getValue)
        .sum();
  }

  private static int countAllStatus(Stream<Map.Entry<TaxonomicStatus, Map<Rank, Integer>>> stream) {
    return stream
        .flatMapToInt(m-> m.getValue().values().stream().mapToInt(Integer::intValue))
        .sum();
  }
}
