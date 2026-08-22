package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxGroup;

import java.util.*;

public interface UsageMatcherStore extends UsageSink, AutoCloseable {

  default int analyze(TaxGroupAnalyzer analyzer){
    int noCounter = 0;
    LOG.info("Analyze tax groups for all usages for dataset {}", datasetKey());
    for (var u : all()) {
      var cl = getClassification(u.getId());
      var tg = analyzer.analyze(u, cl);
      update(u.getId(), tg);
    }
    LOG.info("Tax groups analyzed for dataset {} with {} usages having no group", datasetKey(), noCounter);
    return noCounter;
  }

  int size();

  /**
   * @return number of distinct canonical name index ids held in the inverted canonical index
   */
  default int canonicalSize() {
    int cnt = 0;
    for (var id : allCanonicalIds()) {
      cnt++;
    }
    return cnt;
  }

  default boolean isEmpty() {
    return size() < 1;
  }

  /**
   * Candidates for a match, in the shape the matcher wants them: with a classification.
   *
   * <p>The classification of each candidate is resolved lazily, on first access. Most candidates are
   * dropped by the cheap filters in {@link UsageMatcher} - bare name, rank, authorship, year, code - which
   * only look at the candidate's own fields, so their parents are never walked. That matters for canonical
   * names shared by a pathological number of usages: canonicalisation strips strain and clone identifiers,
   * so a name like "? bacterium" collects tens of thousands of usages in an environmental dataset.
   *
   * @param canonId a canonical names index id
   * @return list of matching usages that act as candidates for the match
   */
  default List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalId(int canonId) {
    var names = simpleNamesByCanonicalId(canonId);
    var list = new ArrayList<SimpleNameClassified<SimpleNameCached>>(names.size());
    for (var sn : names) {
      list.add(new LazyClassifiedUsage(sn, this));
    }
    return list;
  }

  List<SimpleNameCached> simpleNamesByCanonicalId(int canonId);

  /**
   * @param usageID the id to start retrieving the classification from
   * @return classification including and starting with the given usageID
   * @throws NotFoundException
   */
  default List<SimpleNameCached> getClassification(String usageID) throws NotFoundException {
    List<SimpleNameCached> classification = new ArrayList<>();
    addParents(classification, usageID, new HashSet<>());
    return classification;
  }

  private void addParents(List<SimpleNameCached> classification, String parentKey, Set<String> visitedIDs) throws NotFoundException {
    if (parentKey != null) {
      SimpleNameCached p = get(parentKey);
      if (p == null) {
        LOG.warn("Missing usage {}", parentKey);
        throw NotFoundException.notFound(NameUsage.class, parentKey);
      }
      visitedIDs.add(parentKey);
      classification.add(p);
      if (p.getParent() != null) {
        if (visitedIDs.contains(p.getParent())) {
          throw new IllegalStateException("Bad classification tree with parent circles involving " + p);
        } else {
          addParents(classification, p.getParent(), visitedIDs);
        }
      }
    }
  }

  SimpleNameCached get(String usageID) throws NotFoundException;

  void update(String usageID, TaxGroup group);

  Iterable<SimpleNameCached> all();

  Iterable<Integer> allCanonicalIds();

  default SimpleNameClassified<SimpleNameCached> getSNClassified(String id) throws NotFoundException {
    var snc = new SimpleNameClassified<SimpleNameCached>(get(id));
    snc.setClassification(getClassification(snc.getParentId()));
    return snc;
  }

  /**
   * Moves the taxon given to a new parent by updating the parent_id
   * @param usageID the taxon to update
   * @param parentId the new parentId to assign
   */
  void updateParentId(String usageID, String parentId);

  @Override
  void close();

}
