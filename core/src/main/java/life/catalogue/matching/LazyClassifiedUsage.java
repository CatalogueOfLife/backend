package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.List;

import org.gbif.nameparser.api.Rank;

/**
 * A match candidate whose classification is only walked when something actually asks for it.
 *
 * <p>Resolving a classification means following the parent chain usage by usage, which for the Postgres
 * backed store is a query per ancestor. Most candidates never get that far: they are dropped by the rank,
 * authorship or code filters that only read the candidate's own fields.
 */
class LazyClassifiedUsage extends SimpleNameClassified<SimpleNameCached> {
  private final UsageMatcherStore store;
  private boolean resolved = false;

  LazyClassifiedUsage(SimpleNameCached sn, UsageMatcherStore store) {
    super(sn);
    this.store = store;
  }

  @Override
  public List<SimpleNameCached> getClassification() {
    if (!resolved) {
      setClassification(store.getClassification(getParentId()));
      resolved = true;
    }
    return super.getClassification();
  }

  @Override
  public boolean hasClassification() {
    var cl = getClassification();
    return cl != null && !cl.isEmpty();
  }

  @Override
  public SimpleNameCached getByRank(Rank rank) {
    getClassification();
    return super.getByRank(rank);
  }
}
