package life.catalogue.common.concurrent;

import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.RankedID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An atomic name usage counter with basic counts for synonyms, taxa and taxa by rank.
 */
public class UsageCounter {
  private final AtomicInteger taxCounter = new AtomicInteger();
  private final AtomicInteger synCounter = new AtomicInteger();
  private final AtomicInteger bareCounter = new AtomicInteger();
  private final Map<Rank, AtomicInteger> rankCounter = new HashMap<>();

  public synchronized void clear() {
    taxCounter.set(0);
    synCounter.set(0);
    bareCounter.set(0);
    rankCounter.clear();
  }

  public synchronized void set(UsageCounter other) {
    taxCounter.set(other.taxCounter.get());
    synCounter.set(other.synCounter.get());
    bareCounter.set(other.bareCounter.get());
    rankCounter.clear();
    other.rankCounter.forEach(rankCounter::put);
  }

  public void inc(TaxonomicStatus status, Rank rank) {
    if (status == null || status.isBareName()) {
      bareCounter.incrementAndGet();
    } else if (status.isSynonym()) {
      synCounter.incrementAndGet();
    } else if (status.isTaxon()) {
      taxCounter.incrementAndGet();
      rankCounter.computeIfAbsent(rank, (r) -> new AtomicInteger());
      rankCounter.get(rank).incrementAndGet();
    }
  }

  public void inc(NameUsage u) {
    inc(u.getStatus(), u.getRank());
  }

  public void inc(RankedID u) {
    inc(null, u.getRank());
  }

  public void inc(SimpleName u) {
    inc(u.getStatus(), u.getRank());
  }

  /**
   * @return number of all usages counted, regardless of its status or rank.
   */
  public int size() {
    return taxCounter.get() + synCounter.get() + bareCounter.get();
  }

  public boolean isEmpty() {
    return taxCounter.get() == 0 && synCounter.get() == 0 && bareCounter.get() == 0;
  }

  public AtomicInteger getTaxCounter() {
    return taxCounter;
  }

  public AtomicInteger getSynCounter() {
    return synCounter;
  }

  public AtomicInteger getBareCounter() {
    return bareCounter;
  }

  public Map<Rank, AtomicInteger> getRankCounter() {
    return rankCounter;
  }

  public void putRankCount(Rank rank, int count) {
    rankCounter.computeIfAbsent(rank, (r) -> new AtomicInteger());
    rankCounter.get(rank).set(count);
  }

  public synchronized void putRankCounter(Map<Rank, Integer> map) {
    for (Map.Entry<Rank, Integer> x : map.entrySet()) {
      rankCounter.put(x.getKey(), new AtomicInteger(x.getValue()));
    }
  }

  public Map<Rank, Integer> getRankCounterMap() {
    Map<Rank, Integer> map = new HashMap<>(rankCounter.size());
    for (Map.Entry<Rank, AtomicInteger> x : rankCounter.entrySet()) {
      map.put(x.getKey(), x.getValue().get());
    }
    return map;
  }
}
