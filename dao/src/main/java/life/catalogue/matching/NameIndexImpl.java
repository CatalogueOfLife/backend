package life.catalogue.matching;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.ScientificName;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.func.Predicates;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.matching.authorship.AuthorComparator;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

/**
 * NameMatching implementation that is backed by a generic store with a list of names keyed to their normalised
 * canonical name using the SciNameNormalizer.normalize() method.
 */
public class NameIndexImpl implements NameIndex {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexImpl.class);
  private static final Set<NameType> INDEX_NAME_TYPES = ImmutableSet.of(
      NameType.SCIENTIFIC, NameType.HYBRID_FORMULA, NameType.VIRUS, NameType.OTU
  );
  
  private final NameIndexStore store;
  private final AuthorComparator authComp;
  private final SqlSessionFactory sqlFactory;

  /**
   * @param sqlFactory sql session factory to talk to the data store backend if needed for inserts or initial loading
   * @throws IllegalStateException when db is in a bad state
   */
  public NameIndexImpl(NameIndexStore store, AuthorshipNormalizer normalizer, SqlSessionFactory sqlFactory) {
    this.store = store;
    this.authComp = new AuthorComparator(normalizer);
    this.sqlFactory = Preconditions.checkNotNull(sqlFactory);
  }
  
  private int countPg() {
    try (SqlSession s = sqlFactory.openSession()) {
      return s.getMapper(NamesIndexMapper.class).count();
    }
  }
  
  private void loadFromPg() {
    store.clear();
    LOG.info("Loading names from postgres into names index");
    try (SqlSession s = sqlFactory.openSession()) {
      NamesIndexMapper mapper = s.getMapper(NamesIndexMapper.class);
      mapper.processAll().forEach(this::addFromPg);
      LOG.info("Loaded {} names from postgres into names index", store.count());
    }
  }

  private void addFromPg(IndexName name) {
    final String key = key(name);
    store.add(key, name);
  }

  @Override
  public NameMatch match(Name name, boolean allowInserts, boolean verbose) {
    NameMatch m;

    List<IndexName> candidates = store.get(key(name));
    if (candidates != null) {
      m = matchCandidates(name, candidates);
      if (verbose) {
        if (m.hasMatch()) {
          candidates.remove(m.getName());
        }
        m.setAlternatives(candidates);
      } else {
        m.setAlternatives(null);
      }

    } else {
      m = NameMatch.noMatch();
    }

    if (allowInserts && needsInsert(m, name)) {
      m = tryToAdd(name, m, verbose);
    }
    LOG.debug("Matched {} => {}", name.getLabel(), m);
    return m;
  }

  private static boolean needsInsert(NameMatch m, Name name){
    return (!m.hasMatch() || m.getType() == MatchType.CANONICAL) && INDEX_NAME_TYPES.contains(name.getType());
  }

  @Override
  public IndexName get(Integer key) {
    var n = store.get(key);
    return n;
  }

  @Override
  public Collection<IndexName> byCanonical(Integer key) {
    return store.byCanonical(key);
  }

  /**
   * Normalize to just 7 rank buckets:
   *
   * SUPRAGENERIC_NAME for anything above the family group, i.e. suprafamily
   * FAMILY for any family group name MEGAFAMILY - INFRATRIBE
   * GENUS for genus group names GENUS - INFRAGENERIC_NAME
   * SPECIES for SPECIES_AGGREGATE - SPECIES
   * SUBSPECIES for INFRASPECIFIC_NAME - CONVARIETY
   * VARIETY for INFRASUBSPECIFIC_NAME - STRAIN
   * UNRANKED
   * @param r
   * @return
   */
  static Rank normRank(Rank r) {
    if (r == null || r == Rank.OTHER || r == Rank.UNRANKED) {
      return Rank.UNRANKED;

    } else if (r.isFamilyGroup()) {
      return Rank.FAMILY;

    } else if (r.isGenusGroup()) {
      return Rank.GENUS;

    } else if (r.isSuprageneric()) {
      return Rank.SUPRAGENERIC_NAME;

    } else if (r == Rank.SPECIES_AGGREGATE || r == Rank.SPECIES) {
      return Rank.SPECIES;

    } else if (r.ordinal() >= Rank.INFRASUBSPECIFIC_NAME.ordinal()) {
      return Rank.VARIETY;

    } else if (r.ordinal() >= Rank.INFRASPECIFIC_NAME.ordinal()) {
      return Rank.SUBSPECIES;
    }
    return Rank.UNRANKED;
  }

  /**
   * Does comparison by rank, name & author to pick real match from candidates
   */
  private NameMatch matchCandidates(Name query, final List<IndexName> candidates) {
    final Rank rank = normRank(query.getRank());
    final boolean compareRank = rank != Rank.UNRANKED;
    final boolean isCanonical = !query.hasAuthorship();
    final String queryname = SciNameNormalizer.normalizedAscii(query.getScientificName());
    final String queryfullname = SciNameNormalizer.normalizedAscii(query.getLabel());
    final String queryauthorship = Strings.nullToEmpty(SciNameNormalizer.normalizedAscii(query.getAuthorship()));
    // calculate score by rank, nomCode & authorship
    // immediately filtering no matches with a negative score
    int bestScore = 0;
    final List<IndexName> matches = new ArrayList<>();
    for (IndexName n : candidates) {
      // 0 to 5
      int score = 0;
      
      // make sure rank match up exactly if part of query
      if (compareRank && !match(rank, n.getRank())) {
        continue;
      }

      // we only want matches without an authorship
      if (isCanonical && n.hasAuthorship()) {
        continue;
      }

      // exact full name match: =5
      if (queryfullname.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.getLabel()))) {
        score = 5;
        
      } else {

        // authorship comparison only for non canonical queries/matches
        if (!isCanonical) {
          // remove different authorships or
          // +1 for equal authorships
          // +2 for exact equal authorship strings
          Equality aeq = authComp.compare(query, n);
          if (aeq == Equality.DIFFERENT) {
            continue;
          }

          if (queryauthorship.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.getAuthorship()))) {
            score += 3;
          } else if (aeq == Equality.EQUAL) {
            score += 1;
          }
        }

        // exact canonical name match: +1
        if (queryname.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.getScientificName()))) {
          score += 1;
        }
      }
      bestScore = addOrRemove(score, n, bestScore, matches);
    }

    NameMatch m = new NameMatch();
    if (matches.isEmpty()) {
      m.setType(MatchType.NONE);

    } else if (matches.size() == 1) {
      IndexName m0 = matches.get(0);
      m.setName(m0);
      if (query.getLabel().equalsIgnoreCase(m0.getLabel())) {
        m.setType(MatchType.EXACT);
      } else if (!isCanonical && !m0.hasAuthorship() && query.getScientificName().equalsIgnoreCase(m0.getLabel())) {
        m.setType(MatchType.CANONICAL);
      } else {
        m.setType(MatchType.VARIANT);
      }

    } else {
      m.setType(MatchType.AMBIGUOUS);
      // multiple, ambiguous matches. Pick the canonical one out of them
      // This can happen when:
      //  a) no rank was given
      //  b) the authorship matches various authorships, e.g. if only the basionym or year is given

      // pick canonical
      if (matches.stream().anyMatch(Predicates.not(IndexName::hasAuthorship))) {
        matches.removeIf(IndexName::hasAuthorship);
        if (matches.size()==1) {
          m.setType(MatchType.CANONICAL);
        }
      }

      // log a warning if we still have more than one match so we can maybe refine the algorithm in the future
      if (compareRank && matches.size() > 1) {
        LOG.debug("Ambiguous match ({} hits) for {} {}", matches.size(), query.getRank(), query.getLabel());
      }
      // we pick the lowest key to guarantee a stable outcome in all cases - even if we dont have a canonical (should not really happen)
      IndexName earliest = matches.get(0);
      for (IndexName n : matches) {
        if (n.getKey() < earliest.getKey()) {
          earliest = n;
        }
      }
      m.setName(earliest);
      return m;
    }
    m.setAlternatives(candidates);
    return m;
  }
  
  /**
   * @return new best score
   */
  private int addOrRemove(int score, IndexName n, int bestScore, List<IndexName> matches) {
    if (score < bestScore) {
      //LOG.debug("Worse match {}<{}: {}", score, bestScore, n.canonicalNameComplete());
      return bestScore;
    }
    
    if (score > bestScore) {
      //LOG.debug("Better match {}>{}: {}", score, bestScore, n.canonicalNameComplete());
      matches.clear();
    } else {
      //LOG.debug("Same match {}={}: {}", score, bestScore, n.canonicalNameComplete());
    }
    matches.add(n);
    return score;
  }

  private IndexName getCanonical(String key) {
    List<IndexName> matches = store.get(key);
    // make sure name has no authorship and code is matching if it was part of the "query"
    matches.removeIf(IndexName::hasAuthorship);
    // just in case we have multiple results make sure to have a stable return by selecting the lowest, i.e. oldes key
    IndexName lowest = null;
    for (IndexName n : matches) {
      if (lowest == null || lowest.getKey() > n.getKey()) {
        lowest = n;
      }
    }
    return lowest;
  }

  @Override
  public void reset() {
    LOG.warn("Removing all entries from the names index store");
    store.clear();
    try (SqlSession session = sqlFactory.openSession(true)) {
      LOG.info("Truncating all name matches");
      session.getMapper(NameMatchMapper.class).truncate();
      LOG.warn("Truncating the names index postgres table");
      session.getMapper(NamesIndexMapper.class).truncate();
    }
  }

  @Override
  public int size() {
    return store.count();
  }

  @Override
  public Iterable<IndexName> all() {
    return store.all();
  }

  /**
   * We synchronize this method to only ever allow one write at a time to avoid duplicates.
   * As we do allow concurrent reads through the main match method
   * we can get parallel queries for the exact same name not previously existing, especially when rebuilding the index concurrently.
   * As these concurrent reads would all result in no matches which would subsequently becomes writes,
   * we need to make sure here again that the name indeed did not yet exist.
   */
  public synchronized NameMatch tryToAdd(Name orig, NameMatch match, boolean verbose) {
    var match2 = match(orig, false, verbose);
    if (needsInsert(match2, orig)) {
      // verified we still do not have that name - insert the original match for real!
      IndexName n = new IndexName(orig);
      add(n);
      match.setName(n);
      match.setType(MatchType.EXACT);
      LOG.debug("Inserted: {}", match.getName().getLabel());
      return match;
    }
    return match2;
  }

  @Override
  public void add(IndexName name) {
    final String key = key(name);
    name.setRank(normRank(name.getRank()));

    try (SqlSession s = sqlFactory.openSession(true)) {
      NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);

      name.setCreatedBy(Users.MATCHER);
      name.setModifiedBy(Users.MATCHER);

      addThreadSafe(key, name, nim);
    }
  }

  private void addThreadSafe(final String key, IndexName name, NamesIndexMapper nim) {
    if (name.hasAuthorship()) {
      // make sure there exists a canonical name without authorship already
      IndexName canonical = getCanonical(key);
      if (canonical == null) {
        // insert new canonical
        canonical = new IndexName();
        canonical.setScientificName(name.getScientificName());
        canonical.setRank(name.getRank());
        canonical.setCode(name.getCode());
        canonical.setUninomial(name.getUninomial());
        canonical.setGenus(name.getGenus());
        canonical.setSpecificEpithet(name.getSpecificEpithet());
        canonical.setInfragenericEpithet(name.getInfragenericEpithet());
        canonical.setInfraspecificEpithet(name.getInfraspecificEpithet());
        canonical.setCultivarEpithet(name.getCultivarEpithet());
        canonical.setCreatedBy(Users.MATCHER);
        canonical.setModifiedBy(Users.MATCHER);
        createCanonical(nim, key, canonical);
      }
      name.setCanonicalId(canonical.getKey());
      nim.create(name);
      store.add(key, name);

    } else {
      createCanonical(nim, key, name);
    }
  }

  private void createCanonical(NamesIndexMapper nim, String key, IndexName cn){
    // mybatis default canonicalID to the newly created key in the database...
    nim.create(cn);
    // ... but the instance is not updated automatically
    cn.setCanonicalId(cn.getKey());
    store.add(key, cn);
  }

  /**
   * @return A pure ASCII key based on the scientific name
   */
  private static String key(ScientificName n) {
    return StringUtils.replaceNonAscii(SciNameNormalizer.normalize(n.getScientificName()), '*');
  }
  
  /**
   * @return true if the ranks given are indicating matching names and do not contradict each other
   */
  private static boolean match(Rank r1, Rank r2) {
    if (r1 == null || r1 == Rank.UNRANKED ||
        r2 == null || r2 == Rank.UNRANKED) return true;
    
    // allow all suprageneric ranks to match
    if (r1.isSuprageneric() && r2.isSuprageneric()) {
      return true;
    }
    Boolean infraTest = matchInfraName1(r1, r2);
    if (infraTest == null) {
      infraTest = matchInfraName1(r2, r1);
    }
    if (infraTest != null) {
      return infraTest;
    } else {
      return r1 == r2;
    }
  }
  
  /**
   * @return true or false if clearly matches or doesnt. Null if we dont know yet
   */
  private static Boolean matchInfraName1(Rank r1, Rank r2) {
    if (r1 == Rank.SPECIES_AGGREGATE) {
      return r2 == Rank.SPECIES || r2 == Rank.SPECIES_AGGREGATE;
      
    } else if (r1 == Rank.INFRASPECIFIC_NAME) {
      return r2.isInfraspecific();
      
    } else if (r1 == Rank.INFRASUBSPECIFIC_NAME) {
      return r2.isInfraspecific() && r2 != Rank.SUBSPECIES;
      
    } else if (r1 == Rank.INFRAGENERIC_NAME) {
      return r2.isInfragenericStrictly();
    }
    
    return null;
  }
  
  @Override
  public void start() throws Exception {
    LOG.info("Start names index ...");
    store.start();
    int storeSize = store.count();
    if (storeSize == 0) {
      loadFromPg();
    } else {
      // verify postgres and store match up - otherwise trust postgres
      int pgCount = countPg();
      if (pgCount != storeSize) {
        LOG.warn("Existing name index contains {} names, but postgres has {}. Trust postgres", storeSize, pgCount);
        loadFromPg();
      }
    }
    LOG.info("Started name index with {} names", store.count());
  }

  @Override
  public boolean hasStarted() {
    try {
      store.get("something");
    } catch (UnavailableException e) {
      return false;
    }
    return true;
  }

  /**
   * Convenience method that starts the index and returns it to be used in fluent code
   */
  public NameIndexImpl started() {
    try {
      start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public void stop() throws Exception {
    LOG.info("Stopping names index ...");
    store.stop();
    LOG.info("Names index db stopped");
  }
}
