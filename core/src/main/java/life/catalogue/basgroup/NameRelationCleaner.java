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
 * The rules are those of {@link NameRelationRules}, applied by a {@link NameRelationJudge}, exact duplicates are collapsed
 * without an issue.
 * Relations created by the homotypic grouper are left alone.
 * With a root taxon only pairs of names are judged of which at least one name has a usage in its subtree.
 */
public class NameRelationCleaner {
  private static final Logger LOG = LoggerFactory.getLogger(NameRelationCleaner.class);
  private static final int BATCH_SIZE = 1000;
  private static final NameRelationJudge<NameRelation> JUDGE = new NameRelationJudge<>(
    nr -> new Rel(nr.getType(), nr.getNameId(), nr.getRelatedNameId())
  );

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
   * @return the number of deleted relations
   */
  public int clean() {
    int duplicates = 0;
    int judged = 0;
    final CountMap<Issue> removed = new CountMap<>();
    final CountMap<Issue> flagged = new CountMap<>();
    // name id -> issues for all its usages
    final Map<String, Set<Issue>> issues = new HashMap<>();
    try (SqlSession session = factory.openSession(false)) {
      var nrm = session.getMapper(NameRelationMapper.class);
      List<NameRelation> rels = new ArrayList<>();
      for (var nr : nrm.listConflictCandidates(datasetKey)) {
        if (!Objects.equals(nr.getCreatedBy(), Users.HOMOTYPIC_GROUPER)) {
          rels.add(nr);
        }
      }
      if (taxonID != null) {
        limitToSubtree(session, rels);
      }
      LOG.info("Check {} name relations sharing their names with another relation or of a spelling correction in dataset {}",
        rels.size(), datasetKey);
      var names = loadNames(session, rels);

      for (var pair : JUDGE.judge(rels, names::get)) {
        for (var nr : pair.duplicates()) {
          LOG.debug("Remove duplicate {} relation between {} and {}", nr.getType(), pair.n1().getLabel(), pair.n2().getLabel());
          nrm.delete(DSID.of(datasetKey, nr.getId()));
          duplicates++;
        }
        for (var v : pair.verdicts()) {
          if (v.remove()) {
            LOG.debug("Remove {} relation between {} and {}: {}",
              v.relation().getType(), pair.n1().getLabel(), pair.n2().getLabel(), v.issue());
            nrm.delete(DSID.of(datasetKey, v.relation().getId()));
            removed.inc(v.issue());
          } else {
            flagged.inc(v.issue());
          }
          issues.computeIfAbsent(pair.n1().getId(), k -> EnumSet.noneOf(Issue.class)).add(v.issue());
          issues.computeIfAbsent(pair.n2().getId(), k -> EnumSet.noneOf(Issue.class)).add(v.issue());
        }
        if (++judged % BATCH_SIZE == 0) {
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
   * Removes all relations of which neither name has a usage in the subtree of the root taxon.
   * Only the names of the relations are kept while the subtree is traversed, so a large subtree costs no memory.
   */
  private void limitToSubtree(SqlSession session, List<NameRelation> rels) {
    Set<String> candidates = nameIds(rels);
    Set<String> inScope = new HashSet<>();
    var traversal = TreeTraversalParameter.dataset(datasetKey, taxonID);
    traversal.setSynonyms(true);
    PgUtils.consume(() -> session.getMapper(NameUsageMapper.class).processTreeLinneanUsage(traversal, false, false), u -> {
      if (candidates.contains(u.getNameId())) {
        inScope.add(u.getNameId());
      }
    });
    rels.removeIf(nr -> !inScope.contains(nr.getNameId()) && !inScope.contains(nr.getRelatedNameId()));
    LOG.info("Limit the cleanup of dataset {} to {} name relations in the subtree of {}", datasetKey, rels.size(), taxonID);
  }

  private static Set<String> nameIds(Collection<NameRelation> rels) {
    Set<String> ids = new HashSet<>();
    for (var nr : rels) {
      ids.add(nr.getNameId());
      if (nr.getRelatedNameId() != null) {
        ids.add(nr.getRelatedNameId());
      }
    }
    return ids;
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

  private Map<String, Name> loadNames(SqlSession session, Collection<NameRelation> rels) {
    Set<String> ids = nameIds(rels);
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
