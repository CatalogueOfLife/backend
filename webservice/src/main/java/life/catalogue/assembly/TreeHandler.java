package life.catalogue.assembly;

import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.IgnoreReason;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.func.ThrowingConsumer;

import org.gbif.nameparser.api.Rank;

import java.util.Map;
import java.util.Objects;

/**
 * Consumers should not expect the accepted property of a Synonym to exist!
 * Use the parentID property instead to lookup the accepted name if needed.
 */
public interface TreeHandler extends ThrowingConsumer<NameUsageBase, InterruptedException>, AutoCloseable {

  void reset() throws InterruptedException;

  /**
   * @return true if exception had been thrown during handling of records
   */
  boolean hasThrown();

  void copyRelations();

  Map<IgnoreReason, Integer> getIgnoredCounter();

  int getDecisionCounter();

  @Override
  void close() throws InterruptedException;

  class Usage {
    String id;
    Rank rank;
    TaxonomicStatus status;

    Usage(String id, Rank rank, TaxonomicStatus status) {
      this.id = id;
      this.rank = rank;
      this.status = status;
    }
    Usage(SimpleName sn) {
      this(sn.getId(), sn.getRank(), sn.getStatus());
    }

    Usage(NameUsage nu) {
      this(nu.getId(), nu.getRank(), nu.getStatus());
    }
  }

  class RanKnName {
    final Rank rank;
    final String name;

    public RanKnName(Rank rank, String name) {
      this.rank = rank;
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RanKnName ranKnName = (RanKnName) o;
      return rank == ranKnName.rank &&
             Objects.equals(name, ranKnName.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(rank, name);
    }
  }

}
