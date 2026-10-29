package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.FormattableName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.matching.similarity.ModifiedDamerauLevenshtein;

import java.util.*;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

/**
 * Removes name relations of the data that contradict the names they relate or each other,
 * before the homotypic grouping of an extended release relies on them.
 * Datasets keep these relations as they were imported, only the release drops them.
 * <ul>
 *   <li>A spelling correction between names that differ in far more than their spelling,
 *   e.g. WoRMS relating an accepted name to the misspelling of one of its synonyms.
 *   It would merge distinct homotypic groups: https://github.com/CatalogueOfLife/backend/issues/1662</li>
 *   <li>Two names related both as basionym and as spelling correction keep the one relation that fits them:
 *   the spelling correction for the same combination spelled differently, the basionym for another combination.
 *   WoRMS exports misspellings with both: https://github.com/CatalogueOfLife/data/issues/1815</li>
 * </ul>
 * Relations created by the homotypic grouper are left alone.
 */
public class NameRelationCleaner {
  private static final Logger LOG = LoggerFactory.getLogger(NameRelationCleaner.class);
  private static final int MIN_SPELLING_EDITS = 3;
  private static final ModifiedDamerauLevenshtein SPELLING_DISTANCE = new ModifiedDamerauLevenshtein();
  private static final int BATCH_SIZE = 1000;

  private final SqlSessionFactory factory;
  private final int datasetKey;

  public NameRelationCleaner(SqlSessionFactory factory, int datasetKey) {
    this.factory = factory;
    this.datasetKey = datasetKey;
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
    int implausible = 0;
    int conflicting = 0;
    int checked = 0;
    try (SqlSession session = factory.openSession(false)) {
      var nrm = session.getMapper(NameRelationMapper.class);
      Map<NamePair, List<NameRelation>> pairs = new HashMap<>();
      for (var nr : nrm.listSpellingConflicts(datasetKey)) {
        if (nr.getRelatedNameId() != null && !Objects.equals(nr.getCreatedBy(), Users.HOMOTYPIC_GROUPER)) {
          pairs.computeIfAbsent(NamePair.of(nr), k -> new ArrayList<>()).add(nr);
        }
      }
      LOG.info("Check {} name pairs related by a spelling correction in dataset {}", pairs.size(), datasetKey);
      var names = loadNames(session, pairs.keySet());

      for (var entry : pairs.entrySet()) {
        var n1 = names.get(entry.getKey().id1());
        var n2 = names.get(entry.getKey().id2());
        if (n1 == null || n2 == null) continue;
        var rels = entry.getValue();
        if (!isPlausibleSpellingCorrection(n1, n2)) {
          implausible += delete(rels, NomRelType.SPELLING_CORRECTION, n1, n2, nrm);
        }
        boolean basionym = rels.stream().anyMatch(nr -> nr.getType() == NomRelType.BASIONYM);
        boolean spelling = rels.stream().anyMatch(nr -> nr.getType() == NomRelType.SPELLING_CORRECTION);
        if (basionym && spelling) {
          var wrong = wrongType(n1, n2);
          if (wrong != null) {
            conflicting += delete(rels, wrong, n1, n2, nrm);
          } else {
            LOG.debug("Keep basionym and spelling correction between {} and {}", n1.getLabel(), n2.getLabel());
          }
        }
        if (++checked % BATCH_SIZE == 0) {
          session.commit();
        }
      }
      session.commit();
    }
    LOG.info("Removed {} implausible spelling corrections and {} relations contradicting another one of the same names in dataset {}",
      implausible, conflicting, datasetKey);
    return implausible + conflicting;
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

  /**
   * Deletes all relations of the given type from the list and the database.
   * @return the number of deleted relations
   */
  private int delete(List<NameRelation> rels, NomRelType type, Name n1, Name n2, NameRelationMapper nrm) {
    int counter = 0;
    var iter = rels.iterator();
    while (iter.hasNext()) {
      var nr = iter.next();
      if (nr.getType() == type) {
        LOG.debug("Remove {} relation between {} and {}", type, n1.getLabel(), n2.getLabel());
        nrm.delete(DSID.of(datasetKey, nr.getId()));
        iter.remove();
        counter++;
      }
    }
    return counter;
  }

  /**
   * @return true if the canonical names of both names are so close that one can be a spelling of the other:
   *   they must not differ in more than a fifth of their characters, with at least 3 edits allowed.
   */
  public static boolean isPlausibleSpellingCorrection(Name n1, Name n2) {
    String c1 = NameFormatter.canonicalName(n1);
    String c2 = NameFormatter.canonicalName(n2);
    if (c1 == null || c2 == null) {
      return true;
    }
    c1 = c1.toLowerCase();
    c2 = c2.toLowerCase();
    int maxEdits = Math.max(MIN_SPELLING_EDITS, Math.max(c1.length(), c2.length()) / 5);
    return SPELLING_DISTANCE.getEditDistance(c1, c2) <= maxEdits;
  }

  /**
   * Decides which relation is wrong when the data relates two names both as basionym and as spelling correction.
   * The same combination spelled differently has no basionym of its own, e.g. Tilapia sparmani and Tilapia sparrmanii.
   * Another combination is no spelling correction, e.g. Chromis sparrmanni and Tilapia sparrmanii.
   *
   * @return the wrong relation type or null if the names do not tell
   */
  @VisibleForTesting
  @Nullable
  static NomRelType wrongType(FormattableName n1, FormattableName n2) {
    if (HomotypicConsolidator.isOrthographicVariant(n1, n2)) {
      return NomRelType.BASIONYM;
    }
    if (isOtherCombination(n1, n2)) {
      return NomRelType.SPELLING_CORRECTION;
    }
    return null;
  }

  /**
   * @return true if both names have a different rank or a genus that differs by more than a misspelling
   */
  private static boolean isOtherCombination(FormattableName n1, FormattableName n2) {
    if (n1.getRank() != null && n2.getRank() != null && n1.getRank() != n2.getRank()) {
      return true;
    }
    return n1.getGenus() != null && n2.getGenus() != null
      && HomotypicConsolidator.GENUS_DISTANCE.getEditDistance(n1.getGenus().toLowerCase(), n2.getGenus().toLowerCase()) > HomotypicConsolidator.MAX_GENUS_DIFF;
  }
}
