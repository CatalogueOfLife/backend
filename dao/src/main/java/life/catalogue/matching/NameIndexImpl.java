package life.catalogue.matching;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.func.Predicates;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.authorship.AuthorComparator;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.UnicodeUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

/**
 * NameMatching implementation that is backed by a generic store with a list of names keyed to their normalised
 * canonical name using the SciNameNormalizer.normalize() method.
 *
 * Ranks are important and kept, apart from uncomparable ranks which are converted to UNRANKED.
 */
public class NameIndexImpl implements NameIndex {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexImpl.class);
  public static final Set<NameType> INDEX_NAME_TYPES = ImmutableSet.of(
      NameType.SCIENTIFIC, NameType.HYBRID_FORMULA, NameType.VIRUS, NameType.OTU, NameType.INFORMAL
  );

  private final boolean verifyIndex; // if true compares counts from index with postgres counts and reloads if wrong
  private final NameIndexStore store;
  private final AuthorComparator authComp;
  private final SqlSessionFactory sqlFactory;

  /**
   * @param sqlFactory sql session factory to talk to the data store backend if needed for inserts or initial loading
   * @throws IllegalStateException when db is in a bad state
   */
  public NameIndexImpl(NameIndexStore store, AuthorshipNormalizer normalizer, SqlSessionFactory sqlFactory, boolean verifyIndex) {
    this.store = store;
    this.authComp = new AuthorComparator(normalizer);
    this.sqlFactory = Preconditions.checkNotNull(sqlFactory);
    this.verifyIndex = verifyIndex;
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
      PgUtils.consume(
        () -> mapper.processAll(),
        this::addFromPg
      );
      LOG.info("Loaded {} names from postgres into names index", store.count());
    }
  }

  private void addFromPg(IndexName name) {
    final String key = key(name);
    store.add(key, name);
  }

  public AuthorComparator getAuthComp() {
    return authComp;
  }

  @Override
  public LocalDateTime created() {
    return store.created();
  }

  @Override
  public NameMatch match(Name name, boolean allowInserts, boolean verbose) throws MatchingException {
    NameMatch m = null;
    try {
      // make sure we have a name
      if (name.getRank() == null) {
        name.setRank(IndexName.CANONICAL_RANK);
      }
      List<IndexName> candidates = store.get(key(name));
      if (candidates != null && !candidates.isEmpty()) {
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

      if (allowInserts && needsInsert(m, name) && eligable(name)) {
        m = tryToAdd(name, m, verbose);
      }
      LOG.debug("Matched {} => {}", name.getLabel(), m);
      return m;

    } catch (UnavailableException e) {
      throw e; // we want this to be passed through

    } catch (Exception e) {
      LOG.error("Error matching >>{}<< match={}", name, m, e);
      throw new MatchingException(name, e);
    }
  }

  /**
   * Check if a new names index entry is needed which is true when
   *  a) there was no match
   *  b) it was a canonical match
   */
  private static boolean needsInsert(NameMatch m, Name name){
    return (!m.hasMatch()
            || (m.getType() == MatchType.CANONICAL && (!name.isCanonical()))
    );
  }

  /**
   * Checks if the given name is eligable to be included in the names index.
   * We allow bad names in the index - it is not a reference, just a lookup.
   * But we exclude no names and placeholders
   */
  private static boolean eligable(Name n){
    return INDEX_NAME_TYPES.contains(n.getType())
      && StringUtils.digitOrAsciiLetters(n.getLabel()) != null;
  }

  @Override
  public IndexName get(Integer key) {
    return store.get(key);
  }

  @Override
  public Collection<IndexName> byCanonical(Integer key) {
    return store.byCanonical(key);
  }

  /**
   * Does comparison by rank, name & author to pick real match from candidates
   */
  private NameMatch matchCandidates(Name query, final List<IndexName> candidates) {
    final Rank rank = query.getRank();
    final boolean hasRank = rank != IndexName.CANONICAL_RANK;
    final boolean hasAuthorship = query.hasAuthorship();
    final String canonicalname = NameFormatter.canonicalName(query);
    final String querycanonical = SciNameNormalizer.normalizedAscii(canonicalname);
    final String queryfullname = SciNameNormalizer.normalizedAscii(query.getLabel());
    final String queryauthorship = Strings.nullToEmpty(SciNameNormalizer.normalizedAscii(query.getAuthorship()));
    // calculate score by rank, nomCode & authorship
    // immediately filtering no matches with a negative score
    int bestScore = 0;
    final List<IndexName> matches = new ArrayList<>();
    for (IndexName n : candidates) {
      // 0 to 6
      int score = 0;

      // for non canonical matches ranks need to match up exactly
      boolean isCanon = n.isCanonical();
      if (!isCanon && hasRank && rank != n.getRank()) {
        continue;
      }
      if (rank == n.getRank()) {
        score += 2;
      }

      // we only want matches without an authorship if none was given
      if (!hasAuthorship && n.hasAuthorship()) {
        continue;
      }

      // we only want matches with an authorship if it was given - or a canonical result
      if (hasAuthorship && !n.isCanonical() && !n.hasAuthorship()) {
        continue;
      }

      // exact full name match incl author = 4
      if (n.hasAuthorship() && queryfullname.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.getLabel()))) {
        score += 4;
        
      } else {

        // authorship comparison only for non canonical queries/matches
        if (hasAuthorship) {
          // remove different authorships or
          // +2 for equal authorships
          // +3 for exact equal authorship strings
          Equality aeq = authComp.compare(query, n);
          if (aeq == Equality.DIFFERENT) {
            continue;
          }

          if (queryauthorship.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.getAuthorship()))) {
            score += 3;
          } else if (aeq == Equality.EQUAL) {
            score += 2;
          } else if (aeq == Equality.UNKNOWN && n.hasAuthorship()) {
            // both have authorships, but its unclear if they match - better snap to the canonical in this case
            score -= 2;
          }
        }

        // avoid exact matches to different infragenerics - unless the match is a canonical one that does not have infrageneric epithets
        if (!n.isCanonical() && n.getRank().isInfragenericStrictly() && !Objects.equals(n.getInfragenericEpithet(), query.getInfragenericEpithet())) {
          continue;
        }

        // exact canonical name match: +1
        if (querycanonical.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(NameFormatter.canonicalName(n)))) {
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
      if (normExact(query.getLabel()).equalsIgnoreCase(normExact(m0.getLabel())) && query.getRank() == m0.getRank()) {
        m.setType(MatchType.EXACT);
      } else if (m0.isCanonical() && (hasAuthorship || !querycanonical.equals(queryfullname) || query.getRank() != m0.getRank())) {
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

      // pick canonical if part of matches
      if (matches.stream().anyMatch(IndexName::isCanonical)) {
        matches.removeIf(Predicates.not(IndexName::isCanonical));
        if (matches.size()==1) {
          m.setType(MatchType.CANONICAL);
        }
      }

      // log a warning if we still have more than one match so we can maybe refine the algorithm in the future
      if (matches.size() > 1) {
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

  private static String normExact(String name) {
    Pattern EXACT = Pattern.compile("[. -]+");
    return EXACT.matcher(name).replaceAll(" ").trim();
  }

  /**
   * @return new best score
   */
  private int addOrRemove(int score, IndexName n, int bestScore, List<IndexName> matches) {
    if (score < bestScore) {
      LOG.trace("Worse match {}<{}: {}", score, bestScore, n.getLabel());
      return bestScore;
    }
    
    if (score > bestScore) {
      LOG.trace("Better match {}>{}: {}", score, bestScore, n.getLabel());
      matches.clear();
    } else {
      LOG.trace("Same match {}={}: {}", score, bestScore, n.getLabel());
    }
    matches.add(n);
    return score;
  }

  private IndexName getCanonical(String key) {
    List<IndexName> matches = store.get(key);
    // make sure the name is a canonical one
    matches.removeIf(n -> !n.isCanonical() || !n.qualifiesAsCanonical());
    // just in case we have multiple results make sure to have a stable return by selecting the lowest, i.e. oldest key
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

  public NameIndexMapDBStore store() {
    return (NameIndexMapDBStore) store;
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
   *
   * This method assumes the name is well formatted and tested to be eligable to be inserted
   */
  private synchronized NameMatch tryToAdd(Name orig, NameMatch match, boolean verbose) {
    LOG.trace("{} match, try to add {}", match.getType(), orig.getLabel());
    var match2 = match(orig, false, verbose);
    if (needsInsert(match2, orig)) {
      LOG.debug("{} match, adding {}", match.getType(), orig.getLabel());
      // verified we still do not have that name - insert the original match for real!
      IndexName n = new IndexName(orig);
      add(n);
      match.setName(n);
      if (n.getScientificName().equalsIgnoreCase(orig.getScientificName())) {
        match.setType(MatchType.EXACT);
      } else {
        match.setType(MatchType.VARIANT);
      }
      return match;
    }
    return match2;
  }

  @Override
  public List<IndexName> delete(int key, boolean rematch){
    var removed = store.delete(key, NameIndexImpl::key);
    // order the canonical last to not break foreign key constraints
    removed.sort(Comparator.comparing(IndexName::isCanonical));
    // remove from db
    var names = new ArrayList<DSID<String>>();
    var archivedNames = new ArrayList<DSID<String>>();
    try (SqlSession s = sqlFactory.openSession(false)) {
      var nim = s.getMapper(NamesIndexMapper.class);
      var nm = s.getMapper(NameMapper.class);
      var nmm = s.getMapper(NameMatchMapper.class);

      var anum = s.getMapper(ArchivedNameUsageMapper.class);
      var anm = s.getMapper(ArchivedNameUsageMatchMapper.class);

      for (var n : removed) {
        // remove matches
        var matches = nm.indexGroupIds(n.getKey());
        names.addAll(matches);
        for (var m : matches) {
          nmm.delete(m);
        }
        // archived matches
        matches = anum.indexGroupIds(n.getKey());
        archivedNames.addAll(matches);
        for (var m : matches) {
          anm.delete(m);
        }
        // remove index name
        nim.delete(n.getKey());
      }
      s.commit();
      LOG.info("Removed index {} and {} more names from names index", key, removed.size()-1);

      // rematch
      if (rematch) {
        LOG.debug("Rematch {} names", names.size());
        for (var n : names) {
          match(nm.get(n), true, false);
        }
        LOG.debug("Rematch {} archived name usages", archivedNames.size());
        for (var n : archivedNames) {
          match(anum.get(n).getName(), true, false);
        }
        LOG.info("Rematched {} names and {} archived usages that had been linked to the removed index name {}", names.size(), archivedNames.size(), key);
      }
    }
    return removed;
  }

  /**
   * Adds a new IndexName to the index, even if it exists already.
   * This method is not thread safe!
   */
  @Override
  public void add(IndexName n) {
    //// make sure this is the same as what the key method is based on !!!
    //n.setScientificName(NameFormatter.canonicalName(n));
    //// rebuild authorship if only existing as parsed version
    //if (n.getAuthorship() == null && n.hasAuthorship()) {
    //  n.setAuthorship(NameFormatter.authorship(n, false));
    //}

    final String key = key(n);
    try (SqlSession s = sqlFactory.openSession()) {
      NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);

      n.setCreatedBy(Users.MATCHER);
      n.setCreated(LocalDateTime.now());
      n.setModifiedBy(Users.MATCHER);
      n.setModified(LocalDateTime.now());

      if (n.qualifiesAsCanonical()) {
        createCanonical(nim, key, n);

      } else {
        // make sure there exists a canonical name without authorship and strongly normalised rank already
        IndexName cn = getCanonical(key);
        if (cn == null) {
          // insert new canonical
          cn = IndexName.newCanonical(n);
          createCanonical(nim, key, cn);
        }
        n.setCanonicalId(cn.getKey());
        nim.create(n);
        store.add(key, n);
      }
      s.commit();
    }
  }

  private void createCanonical(NamesIndexMapper nim, String key, IndexName cn){
    // mybatis defaults canonicalID to the newly created key in the database...
    nim.create(cn);
    // ... but the instance is not updated automatically
    cn.setCanonicalId(cn.getKey());
    store.add(key, cn);
  }

  /**
   * @return A pure ASCII key based on the newly formatted canonical name or scientific name as the fallback
   */
  private static String key(FormattableName n) {
    String origName = NameFormatter.canonicalName(n);
    return UnicodeUtils.replaceNonAscii(SciNameNormalizer.normalize(UnicodeUtils.decompose(origName)).toLowerCase(), '*');
  }
  
  /**
   * @return true if the ranks given are indicating matching names and do not contradict each other
   */
  private static boolean match(Rank r1, Rank r2) {
    if (r1 == null || r1 == Rank.UNRANKED ||
        r2 == null || r2 == Rank.UNRANKED) return true;
    
    // for suprageneric names compare their base rank only
    if (r1.isSuprageneric() && r1.getMajorRank() == r2.getMajorRank()) {
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
        LOG.warn("Existing name index contains {} names, but postgres has {}.", storeSize, pgCount);
        if (verifyIndex) {
          loadFromPg();
        }
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
