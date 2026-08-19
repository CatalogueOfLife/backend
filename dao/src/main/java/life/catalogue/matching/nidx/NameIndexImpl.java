package life.catalogue.matching.nidx;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.EmptySqlSessionFactory;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.MatchingException;
import life.catalogue.matching.authorship.AuthorComparator;


import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.util.UnicodeUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;

/**
 * NameMatching implementation backed by a slim {@code normalized-String -> nidx-int} store.
 *
 * The index is single-tier & canonical-only: a name is bucketed purely by its normalised canonical
 * name (see {@link #key}). A match therefore resolves to the bucket's nidx if present, or - when
 * inserts are allowed - assigns a fresh canonical entry on a miss. Authorship and rank are never
 * stored; homonym separation and EXACT/VARIANT classification live in the usage-match layer.
 */
public class NameIndexImpl implements NameIndex {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexImpl.class);
  // Index every name type except PLACEHOLDER. OTHER covers unparsable-but-real names such as
  // viruses (name-parser v4.2 folded the former VIRUS type into OTHER + NomCode.VIRUS). IDENTIFIER
  // covers identifier pseudo-names (BOLD:, UNITE SH...FU codes; name-parser 5.0 gave these their own
  // type again, having folded them into OTHER in v4) which we still want matchable. Only structureless
  // placeholders are skipped.
  public static final Set<NameType> INDEX_NAME_TYPES = ImmutableSet.of(
      NameType.SCIENTIFIC, NameType.FORMULA, NameType.INFORMAL, NameType.OTHER, NameType.IDENTIFIER
  );

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
  public NameIndexImpl(NameIndexStore store, AuthorshipNormalizer normalizer, @Nullable SqlSessionFactory sqlFactory) {
    this.store = store;
    this.authComp = new AuthorComparator(normalizer);
    hasPg = sqlFactory != null;
    if (sqlFactory == null) {
      LOG.warn("No postgres connection given. Names index will only be kept in files.");
      this.sqlFactory = new EmptySqlSessionFactory();
    } else {
      this.sqlFactory = sqlFactory;
    }
  }

  public void printPgIndex() {
    System.out.println("\nNames Index from postgres:");
    try (SqlSession session = sqlFactory.openSession(true)) {
      session.getMapper(NamesIndexMapper.class).processAll().forEach(System.out::println);
    }
  }

  private void addFromPg(NameIndexEntry name) {
    store.add(name.getNormalized(), name.getKey());
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
    try {
      // make sure we have a rank so the canonical name is built consistently
      if (name.getRank() == null) {
        name.setRank(ScientificName.CANONICAL_RANK);
      }
      final String key = key(name);
      int nidx = store.get(key);
      NameMatch m = nidx > 0 ? NameMatch.match(nidx) : NameMatch.noMatch();
      if (allowInserts && !m.isMatched() && eligable(name)) {
        m = tryToAdd(name, key);
      }
      LOG.debug("Matched {} => {}", name.getLabel(), m);
      return m;

    } catch (UnavailableException e) {
      throw e; // we want this to be passed through

    } catch (Exception e) {
      LOG.error("Error matching >>{}<<", name, e);
      throw new MatchingException(name, e);
    }
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
  public NameIndexEntry get(Integer key) {
    try (SqlSession s = sqlFactory.openSession()) {
      return s.getMapper(NamesIndexMapper.class).get(key);
    }
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

  /**
   * We synchronize this method to only ever allow one write at a time to avoid duplicates.
   * As we do allow concurrent reads through the main match method
   * we can get parallel queries for the exact same name not previously existing, especially when rebuilding the index concurrently.
   * As these concurrent reads would all result in no matches which would subsequently becomes writes,
   * we need to make sure here again that the name indeed did not yet exist.
   *
   * This method assumes the name is well formatted and tested to be eligable to be inserted
   */
  private NameMatch tryToAdd(Name orig, String key) {
    insertLock.lock();
    try {
      // re-check under the lock: another thread on this same JVM may have inserted it in the meantime
      int nidx = store.get(key);
      if (nidx > 0) {
        return NameMatch.match(nidx);
      }
      LOG.debug("Adding new canonical name {}", orig.getLabel());
      int id = createCanonical(orig, key);
      return NameMatch.match(id);
    } finally {
      insertLock.unlock();
    }
  }

  /**
   * Inserts a fresh canonical entry for the given name and returns its assigned nidx.
   * With postgres this is an atomic assign-on-miss: {@link NamesIndexMapper#createOnConflict} inserts a
   * new row and sets its generated key, or - if a concurrent rebuild already inserted the same normalized
   * bucket - inserts nothing (leaving key null) so we fall back to the existing winner via
   * {@link NamesIndexMapper#getKeyByNormalized}. This is the only safeguard against duplicate rows across
   * independent index instances sharing one postgres; the JVM-local insertLock only guards a single one.
   */
  private int createCanonical(Name orig, String key) {
    // build the canonical (rankless, authorless) carrier to hand to the mapper insert
    NameIndexEntry cn = NameIndexEntry.canonical(orig, key);
    final int id;
    if (hasPg) {
      try (SqlSession s = sqlFactory.openSession()) {
        NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);
        cn.setKey(null);
        nim.createOnConflict(cn);
        if (cn.getKey() == null) {
          cn.setKey(nim.getKeyByNormalized(key));
        }
        s.commit();
      }
      id = cn.getKey();
    } else {
      id = keyGen.incrementAndGet();
    }
    store.add(key, id);
    return id;
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
    if (hasPg) {
      // the names index is append-only in postgres, so on (re)start we only ever need to catch up
      // with rows added since the store was last stopped - never a full reload, which would be far
      // too slow once the index holds tens of millions of names.
      try (SqlSession s = sqlFactory.openSession()) {
        NamesIndexMapper mapper = s.getMapper(NamesIndexMapper.class);
        int localMax = store.maxKey();
        int pgMax = mapper.maxKey();
        if (pgMax > localMax) {
          final int before = store.count();
          PgUtils.consume(
            () -> mapper.processSince(localMax),
            this::addFromPg
          );
          LOG.info("Loaded {} new names (catch-up from {} to {})", store.count() - before, localMax, pgMax);
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
