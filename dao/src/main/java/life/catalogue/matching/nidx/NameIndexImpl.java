package life.catalogue.matching.nidx;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.EmptySqlSessionFactory;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.MatchingException;
import life.catalogue.matching.authorship.AuthorComparator;

import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.util.UnicodeUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;

/**
 * NameMatching implementation that is backed by a generic store with a list of names keyed to their normalised
 * canonical name using the SciNameNormalizer.normalize() method.
 *
 * Ranks are important and kept, apart from uncomparable ranks which are converted to UNRANKED.
 */
public class NameIndexImpl implements NameIndex {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexImpl.class);
  // Index every name type except PLACEHOLDER. OTHER covers unparsable-but-real names such as
  // viruses (name-parser v4.2 folded the former VIRUS type into OTHER + NomCode.VIRUS) and OTU codes,
  // which we still want matchable; only structureless placeholders are skipped.
  public static final Set<NameType> INDEX_NAME_TYPES = ImmutableSet.of(
      NameType.SCIENTIFIC, NameType.FORMULA, NameType.INFORMAL, NameType.OTHER
  );

  private final boolean verifyIndex; // if true compares counts from index with postgres counts and reloads if wrong
  private final NameIndexStore store;
  private final AuthorComparator authComp;
  private final SqlSessionFactory sqlFactory;
  private final boolean hasPg;
  private final AtomicInteger keyGen = new AtomicInteger(0); // only used when we have no database
  private final ReentrantLock insertLock = new ReentrantLock();
  /**
   * @param sqlFactory sql session factory to talk to the data store backend if needed for inserts or initial loading
   * @throws IllegalStateException when db is in a bad state
   */
  public NameIndexImpl(NameIndexStore store, AuthorshipNormalizer normalizer, @Nullable SqlSessionFactory sqlFactory, boolean verifyIndex) {
    this.store = store;
    this.authComp = new AuthorComparator(normalizer);
    this.verifyIndex = verifyIndex;
    hasPg = sqlFactory != null;
    if (sqlFactory == null) {
      LOG.warn("No postgres connection given. Names index will only be kept in files.");
      this.sqlFactory = new EmptySqlSessionFactory();
    } else {
      this.sqlFactory = sqlFactory;
    }
  }
  
  private int countPg() {
    try (SqlSession s = sqlFactory.openSession()) {
      return s.getMapper(NamesIndexMapper.class).count();
    }
  }

  public void printPgIndex() {
    System.out.println("\nNames Index from postgres:");
    try (SqlSession session = sqlFactory.openSession(true)) {
      session.getMapper(NamesIndexMapper.class).processAll().forEach(System.out::println);
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
   * Check if a new names index entry is needed which is only true when there was no match at all -
   * the index is single-tier & canonical-only, so any match (EXACT or VARIANT) always resolves to
   * the existing canonical entry and never requires a new rank/author specific row.
   */
  private static boolean needsInsert(NameMatch m, Name name){
    return !m.hasMatch();
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
   * The names index is single-tier & canonical-only: every candidate bucket holds at most one
   * canonical entry, so matching purely compares the query's canonical name string against that
   * entry's - no rank or authorship scoring is involved anymore.
   *
   * The bucket lookup (store.get(key(name))) already applies the aggressive, ASCII-folded &
   * stemmed normalization (see #key), so any candidate found here is guaranteed to be the same
   * name modulo spelling/diacritics/stemming. What's left to decide is only whether the query is
   * a byte-for-byte (case/whitespace/punctuation insensitive) reproduction of the stored canonical
   * name (EXACT) or a spelling variant of it, e.g. differing in diacritics, hyphenation or gender
   * ending (VARIANT). We therefore compare the two canonical strings without ASCII-folding them -
   * folding both sides would erase exactly the diacritic differences a VARIANT is meant to flag.
   */
  private NameMatch matchCandidates(Name query, final List<IndexName> candidates) {
    IndexName hit = null;
    for (IndexName n : candidates) {
      if (n.isCanonical()) { hit = n; break; } // one canonical per bucket
    }
    NameMatch m = new NameMatch();
    if (hit == null) {
      m.setType(MatchType.NONE);
      return m;
    }
    m.setName(hit);
    m.setType(classifyCanonicalMatch(query, hit));
    m.setAlternatives(candidates);
    return m;
  }

  /**
   * Classifies how closely two names' canonical forms agree: EXACT if their normalized canonical
   * name strings are identical (case insensitively), VARIANT if they only differ by
   * unicode/punctuation/spelling. Used both when matching against an existing candidate and when
   * classifying a freshly inserted entry, so the very same pair of names always yields the same
   * MatchType regardless of insertion order.
   */
  private static MatchType classifyCanonicalMatch(FormattableName a, FormattableName b) {
    String ac = SciNameNormalizer.normalizeWhitespaceAndPunctuation(NameFormatter.canonicalName(a));
    String bc = SciNameNormalizer.normalizeWhitespaceAndPunctuation(NameFormatter.canonicalName(b));
    return ac.equalsIgnoreCase(bc) ? MatchType.EXACT : MatchType.VARIANT;
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
    if (hasPg) {
      try (SqlSession session = sqlFactory.openSession(true)) {
        LOG.info("Truncating all name matches");
        session.getMapper(NameMatchMapper.class).truncate();
        LOG.warn("Truncating the names index postgres table");
        session.getMapper(NamesIndexMapper.class).truncate();
      }
    }
  }

  @Override
  public int size() {
    return store.count();
  }

  @Override
  public NameIndexStore store() {
    return store;
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
  private NameMatch tryToAdd(Name orig, NameMatch match, boolean verbose) {
    insertLock.lock();
    try {
      LOG.trace("{} match, try to add {}", match.getType(), orig.getLabel());
      var match2 = match(orig, false, verbose);
      if (needsInsert(match2, orig)) {
        LOG.debug("{} match, adding {}", match.getType(), orig.getLabel());
        // verified we still do not have that name - insert the original match for real!
        IndexName n = new IndexName(orig);
        add(n);
        match.setName(n);
        // classify with the same canonical-name comparison matchCandidates uses (not orig's raw
        // scientificName, which may still carry a rank marker n's canonical form has dropped), so
        // the very same name is classified identically whether it is the first insert or a later match
        match.setType(classifyCanonicalMatch(orig, n));
        return match;
      }
      return match2;
    } finally {
      insertLock.unlock();
    }
  }

  @Override
  public List<IndexName> delete(int key, boolean rematch){
    var removed = store.delete(key, NameIndexImpl::key);
    // order the canonical last to not break foreign key constraints
    removed.sort(Comparator.comparing(IndexName::isCanonical));
    // remove from db?
    if (hasPg) {
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
    }
    return removed;
  }

  /**
   * Adds a new IndexName to the index, even if it exists already.
   * The names index is single-tier: every entry is a canonical name (standard UNRANKED rank, no authorship).
   * If the given name does not already qualify as canonical it is reduced to its canonical form in place
   * before being inserted (or matched against an existing canonical entry) - no separate rank/author
   * specific child row is ever created.
   * This method is not thread safe!
   */
  @Override
  public void add(IndexName n) {
    if (!n.qualifiesAsCanonical()) {
      // reduce n in place to its canonical form: standard rank, no authorship.
      // we reuse IndexName.newCanonical() to compute the canonical properties, then copy them onto n
      // so that callers holding a reference to n (e.g. NameIndexImpl.tryToAdd) see the persisted canonical entry.
      IndexName cn = IndexName.newCanonical(n);
      n.setRank(cn.getRank());
      n.setUninomial(cn.getUninomial());
      n.setGenus(cn.getGenus());
      n.setInfragenericEpithet(cn.getInfragenericEpithet());
      n.setSpecificEpithet(cn.getSpecificEpithet());
      n.setInfraspecificEpithet(cn.getInfraspecificEpithet());
      n.setCultivarEpithet(cn.getCultivarEpithet());
      n.setScientificName(cn.getScientificName());
      n.setAuthorship(null);
      n.setCombinationAuthorship(new Authorship());
      n.setBasionymAuthorship(new Authorship());
      n.setSanctioningAuthor(null);
    }
    // compute the lookup key from the (now) canonical form. key() ignores rank & authorship, so the
    // value is identical before/after the reduction above, but computing it here keeps intent explicit.
    final String key = key(n);

    n.setCreatedBy(Users.MATCHER);
    n.setCreated(LocalDateTime.now());
    n.setModifiedBy(Users.MATCHER);
    n.setModified(LocalDateTime.now());

    try (SqlSession s = sqlFactory.openSession()) {
      NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);
      // getCanonical returns the existing canonical entry for this key, if any
      IndexName existing = getCanonical(key);
      if (existing == null) {
        createCanonical(nim, key, n);
      } else {
        // reuse the existing canonical entry - never insert a duplicate row
        n.setKey(existing.getKey());
        n.setCanonicalId(existing.getKey());
      }
      s.commit();
    }
  }

  private void createCanonical(NamesIndexMapper nim, String key, IndexName cn){
    // mybatis defaults canonicalID to the newly created key in the database...
    if (hasPg) {
      nim.create(cn);
    } else {
      cn.setKey(keyGen.incrementAndGet());
    }
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
  
  @Override
  public void start() throws Exception {
    LOG.info("Start names index ...");
    store.start();
    int storeSize = store.count();
    if (hasPg) {
      // verify postgres and store match up - otherwise trust postgres
      int pgCount = countPg();
      if (pgCount != storeSize) {
        LOG.warn("Existing name index contains {} names, but postgres has {}.", storeSize, pgCount);
        if (verifyIndex) {
          loadFromPg();
        }
      }
    } else {
      keyGen.set(store.maxKey());
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
