package life.catalogue.assembly;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.IgnoreReason;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.func.ThrowingConsumer;

import org.gbif.nameparser.api.Rank;

import java.util.Map;

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

  /**
   * @return the last exception thrown or null if no exceptions was ever thrown
   */
  Throwable lastException();

  void copyRelations();

  Map<IgnoreReason, Integer> getIgnoredCounter();

  int getDecisionCounter();

  @Override
  void close() throws InterruptedException;

  class Usage {
    String id;
    String parentId;
    Rank rank;
    TaxonomicStatus status;
    EditorialDecision decision;

    Usage(String id, String parentId, Rank rank, TaxonomicStatus status, EditorialDecision decision) {
      this.id = id;
      this.parentId = parentId;
      this.rank = rank;
      this.status = status;
      this.decision = decision;
    }

    @Override
    public String toString() {
      return String.format("%s %s %s (%s)", status, rank, id, parentId);
    }
  }

}
