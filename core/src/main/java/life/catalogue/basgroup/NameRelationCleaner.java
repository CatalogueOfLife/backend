package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Users;
import life.catalogue.basgroup.NameRelationRules.Rel;
import life.catalogue.common.collection.CountMap;
import life.catalogue.dao.IssueAdder;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import java.util.*;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

/**
 * Removes name relations of the data that do not fit the names they relate, contradict each other or merely repeat
 * another one, before the homotypic grouping of an extended release relies on them.
 * Datasets keep these relations as they were imported and are only flagged, the release removes them and flags
 * the usages of both names, so it stays visible why a relation is gone.
 * The rules are those of {@link NameRelationRules}, exact duplicates are collapsed without an issue.
 * Relations created by the homotypic grouper are left alone.
 * With a root taxon only pairs of names are judged of which at least one name has a usage in its subtree.
 */
public class NameRelationCleaner {
  private static final Logger LOG = LoggerFactory.getLogger(NameRelationCleaner.class);
  private static final int BATCH_SIZE = 1000;

  private final SqlSessionFactory factory;
  private final int datasetKey;
  @Nullable
  private final String taxonID;

  public NameRelationCleaner(SqlSessionFactory factory, int datasetKey) {
    this(factory, datasetKey, null);
  }

  /**
   * @param taxonID optional root taxon limiting the cleanup to its subtree, synonyms included
   */
  public NameRelationCleaner(SqlSessionFactory factory, int datasetKey, @Nullable String taxonID) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    this.taxonID = taxonID;
  }

  /**
   * The two names of a relation, in no particular order.
   */
  private record NamePair(String id1, String id2) {
    static NamePair of(NameRelation nr) {
      return nr.getNameId().compareTo(nr.getRelatedNameId()) <= 0
        ? new NamePair(nr.getNameId(), nr.getRelatedNameId())
        : new NamePair(nr.getRelatedNameId(), nr.getNameId());
    }
  }

  /**
   * @return the number of deleted relations
   */
  public int clean() {
    int duplicates = 0;
    int checked = 0;
    final CountMap<Issue> removed = new CountMap<>();
    final CountMap<Issue> flagged = new CountMap<>();
    // name id -> issues for all its usages
    final Map<String, Set<Issue>> issues = new HashMap<>();
    try (SqlSession session = factory.openSession(false)) {
      var nrm = session.getMapper(NameRelationMapper.class);
      Map<NamePair, List<NameRelation>> pairs = new HashMap<>();
      for (var nr : nrm.listConflictCandidates(datasetKey)) {
        if (nr.getRelatedNameId() != null && !nr.getNameId().equals(nr.getRelatedNameId())
            && !Objects.equals(nr.getCreatedBy(), Users.HOMOTYPIC_GROUPER)) {
          pairs.computeIfAbsent(NamePair.of(nr), k -> new ArrayList<>()).add(nr);
        }
      }
      if (taxonID != null) {
        limitToSubtree(session, pairs);
      }
      LOG.info("Check {} pairs of names with several relations or a spelling correction in dataset {}", pairs.size(), datasetKey);
      var names = loadNames(session, pairs.keySet());

      for (var entry : pairs.entrySet()) {
        var n1 = names.get(entry.getKey().id1());
        var n2 = names.get(entry.getKey().id2());
        if (n1 == null || n2 == null) continue;
        // collapse exact duplicates, keeping the first relation
        Map<Rel, NameRelation> rels = new LinkedHashMap<>();
        for (var nr : entry.getValue()) {
          var rel = new Rel(nr.getType(), nr.getNameId(), nr.getRelatedNameId());
          if (rels.putIfAbsent(rel, nr) != null) {
            LOG.debug("Remove duplicate {} relation between {} and {}", nr.getType(), n1.getLabel(), n2.getLabel());
            nrm.delete(DSID.of(datasetKey, nr.getId()));
            duplicates++;
          }
        }
        Set<Rel> deleted = new HashSet<>();
        for (var v : NameRelationRules.check(n1, n2, rels.keySet())) {
          if (v.remove()) {
            if (deleted.add(v.rel())) {
              LOG.debug("Remove {} relation between {} and {}: {}", v.rel().type(), n1.getLabel(), n2.getLabel(), v.issue());
              nrm.delete(DSID.of(datasetKey, rels.get(v.rel()).getId()));
              removed.inc(v.issue());
            }
          } else {
            flagged.inc(v.issue());
          }
          issues.computeIfAbsent(n1.getId(), k -> EnumSet.noneOf(Issue.class)).add(v.issue());
          issues.computeIfAbsent(n2.getId(), k -> EnumSet.noneOf(Issue.class)).add(v.issue());
        }
        if (++checked % BATCH_SIZE == 0) {
          session.commit();
        }
      }
      session.commit();
    }
    addIssues(issues);
    LOG.info("Removed {} duplicate name relations in dataset {}", duplicates, datasetKey);
    removed.forEach((issue, cnt) -> LOG.info("Removed {} name relations with {} in dataset {}", cnt, issue, datasetKey));
    flagged.forEach((issue, cnt) -> LOG.info("Kept {} name relations flagged with {} in dataset {}", cnt, issue, datasetKey));
    return duplicates + removed.values().stream().mapToInt(Integer::intValue).sum();
  }

  /**
   * Removes all pairs of which neither name has a usage in the subtree of the root taxon.
   * Only the names of the pairs are kept while the subtree is traversed, so a large subtree costs no memory.
   */
  private void limitToSubtree(SqlSession session, Map<NamePair, List<NameRelation>> pairs) {
    Set<String> candidates = new HashSet<>();
    for (var p : pairs.keySet()) {
      candidates.add(p.id1());
      candidates.add(p.id2());
    }
    Set<String> inScope = new HashSet<>();
    var traversal = TreeTraversalParameter.dataset(datasetKey, taxonID);
    traversal.setSynonyms(true);
    PgUtils.consume(() -> session.getMapper(NameUsageMapper.class).processTreeLinneanUsage(traversal, false, false), u -> {
      if (candidates.contains(u.getNameId())) {
        inScope.add(u.getNameId());
      }
    });
    pairs.keySet().removeIf(p -> !inScope.contains(p.id1()) && !inScope.contains(p.id2()));
    LOG.info("Limit the cleanup of dataset {} to {} pairs of names in the subtree of {}", datasetKey, pairs.size(), taxonID);
  }

  private void addIssues(Map<String, Set<Issue>> issues) {
    LOG.info("Flag the usages of {} names with name relation issues in dataset {}", issues.size(), datasetKey);
    try (SqlSession session = factory.openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var adder = new IssueAdder(datasetKey, session);
      issues.forEach((nameId, iss) -> {
        for (var usageId : num.listUsageIDsByNameID(datasetKey, nameId)) {
          adder.addIssues(usageId, iss);
        }
      });
    }
  }

  private Map<String, Name> loadNames(SqlSession session, Collection<NamePair> pairs) {
    Set<String> ids = new HashSet<>();
    for (var p : pairs) {
      ids.add(p.id1());
      ids.add(p.id2());
    }
    var nm = session.getMapper(NameMapper.class);
    Map<String, Name> names = new HashMap<>();
    for (var batch : Iterables.partition(ids, BATCH_SIZE)) {
      for (var n : nm.listByIds(datasetKey, new HashSet<>(batch))) {
        names.put(n.getId(), n);
      }
    }
    return names;
  }
}
