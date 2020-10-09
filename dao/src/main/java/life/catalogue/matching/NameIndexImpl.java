package life.catalogue.matching;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.matching.authorship.AuthorComparator;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

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
      mapper.processAll().forEach(this::add);
      LOG.info("Loaded {} names from postgres into names index", store.count());
    }
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
      }
      
    } else {
      m = NameMatch.noMatch();
    }

    if ((!m.hasMatch() || m.getType() == MatchType.CANONICAL) && allowInserts) {
      if (INDEX_NAME_TYPES.contains(name.getType())) {
        m.setName(add(name));
        m.setType(MatchType.INSERTED);
        LOG.debug("Inserted: {}", m.getName().getLabel());
      } else {
        LOG.debug("Do not insert {} name: {}", name.getType(), name.getLabel());
      }
    }
    LOG.debug("Matched {} => {}", name.getLabel(), m);
    return m;
  }

  @Override
  public IndexName get(Integer key) {
    return store.get(key);
  }

  static Rank normRank(Rank r) {
    if (r == null || r == Rank.OTHER) {
      return Rank.UNRANKED;

    } else if (r.isFamilyGroup()) {
      return Rank.FAMILY;

    } else if (r.isGenusGroup()) {
      return Rank.GENUS;

    } else if (r.isSuprageneric()) {
      return Rank.SUPRAGENERIC_NAME;

    } else if (r == Rank.SPECIES_AGGREGATE) {
      return Rank.SPECIES;

    } else if (r == Rank.INFRASPECIFIC_NAME) {
      return Rank.SUBSPECIES;

    } else if (r == Rank.INFRASUBSPECIFIC_NAME) {
      return Rank.VARIETY;
    }
    return r;
  }

  /**
   * Does comparison by rank, author and nom code to pick real match from candidates
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
    final List<IndexName> matches = Lists.newArrayList();
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
        LOG.info("Ambiguous match ({} hits) for {} {}", matches.size(), query.getRank(), query.getLabel());
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

  @VisibleForTesting
  protected IndexName getCanonical(ScientificName name) {
    List<IndexName> matches = store.get(key(name));
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
    store.clear();
    try (SqlSession session = sqlFactory.openSession()) {
      NamesIndexMapper nim = session.getMapper(NamesIndexMapper.class);
      nim.truncate();
      nim.resetSequence();
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

  public IndexName add(Name orig) {
    IndexName n = new IndexName(orig);
    add(n);
    return n;
  }

  @Override
  public synchronized void add(IndexName name) {
    final String key = key(name);
    name.setRank(normRank(name.getRank()));

    try (SqlSession s = sqlFactory.openSession(true)) {
      NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);

      name.setCreatedBy(Users.MATCHER);
      name.setModifiedBy(Users.MATCHER);
      if (name.hasAuthorship()) {
        // make sure there exists a canonical name without authorship already
        IndexName canonical = getCanonical(name);
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
          nim.create(canonical);
          store.add(key, canonical);
        }
        name.setCanonicalId(canonical.getKey());
      }
      // insert into postgres assigning a key
      nim.create(name);
      store.add(key, name);
    }
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
