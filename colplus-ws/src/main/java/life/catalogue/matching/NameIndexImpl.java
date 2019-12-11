package life.catalogue.matching;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.dao.NameDao;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.importer.IdGenerator;
import life.catalogue.matching.authorship.AuthorComparator;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NameMatching implementation that is backed by a generic store with a list of names keyed to their normalised
 * canonical name using the SciNameNormalizer.normalize() method.
 */
public class NameIndexImpl implements NameIndex {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexImpl.class);
  private static final Set<NameType> INDEX_NAME_TYPES = ImmutableSet.of(
      NameType.SCIENTIFIC, NameType.HYBRID_FORMULA, NameType.VIRUS, NameType.OTU
  );
  
  private final AtomicInteger counter = new AtomicInteger(0);
  private final IdGenerator idGen;
  private final NameIndexStore store;
  private final AuthorComparator authComp;
  private final int datasetKey;
  private final SqlSessionFactory sqlFactory;
  private final NameDao dao;
  private LocalDateTime startedLoading;
  
  
  /**
   * @param datasetKey the dataset the names index is stored in
   * @param sqlFactory sql session factory to talk to the data store backend if needed for inserts or initial loading
   * @throws IllegalStateException when db is in a bad state
   */
  public NameIndexImpl(NameIndexStore store, AuthorshipNormalizer normalizer, int datasetKey, SqlSessionFactory sqlFactory) {
      this.store = store;
      this.authComp = new AuthorComparator(normalizer);
      this.datasetKey = datasetKey;
      this.sqlFactory = Preconditions.checkNotNull(sqlFactory);
      dao = new NameDao(sqlFactory, normalizer);
      int storeSize = store.count();
      if (storeSize == 0) {
        loadFromPg();
      } else {
        // verify postgres and store match up - otherwise trust postgres
        long pgCount = countPg();
        if (pgCount != storeSize) {
          LOG.warn("Existing name index contains {} names, but postgres has {}. Trust postgres", storeSize, pgCount);
          loadFromPg();
        } else {
          counter.set(storeSize);
        }
      }
      LOG.info("Started name index with {} names", counter.get());
      idGen = new IdGenerator(counter::incrementAndGet);
    }
  
  private int countPg() {
    try (SqlSession s = sqlFactory.openSession()) {
      return s.getMapper(NameMapper.class).count(datasetKey);
    }
  }
  
  private void loadFromPg() {
    LOG.info("Loading names from postgres into names index");
    startedLoading = LocalDateTime.now();
    counter.set(0);
    try (SqlSession s = sqlFactory.openSession()) {
      NameMapper mapper = s.getMapper(NameMapper.class);
      mapper.processDataset(datasetKey).forEach(n -> {
        addWithID(n);
        counter.incrementAndGet();
      });
      LOG.info("Loaded {} names from postgres into names index", counter);
    }
  }
  
  public void loadFromPgSinceStart() {
    LOG.info("Loading names from postgres into names index");
    final AtomicInteger newCnt = new AtomicInteger();
    try (SqlSession s = sqlFactory.openSession()) {
      NameMapper mapper = s.getMapper(NameMapper.class);
      mapper.processSince(datasetKey, startedLoading).forEach(n -> {
        addWithID(n);
        counter.incrementAndGet();
        newCnt.incrementAndGet();
      });
      LOG.info("Loaded {} additional names since start {} from postgres into names index", newCnt, startedLoading);
    }
  }
  
  @Override
  public NameMatch match(Name name, boolean allowInserts, boolean verbose) {
    NameMatch m;
    List<Name> candidates = store.get(key(name));
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
    
    if (!m.hasMatch() && allowInserts) {
      if (MatchType.AMBIGUOUS == m.getType()) {
        LOG.debug("Do not insert ambiguous name match: {}", name.canonicalNameWithAuthorship());
      } else if (INDEX_NAME_TYPES.contains(name.getType())) {
        m.setName(insert(name));
        m.setType(MatchType.INSERTED);
        LOG.debug("Inserted: {}", m.getName().canonicalNameWithAuthorship());
      } else {
        LOG.debug("Do not insert {} name: {}", name.getType(), name.canonicalNameWithAuthorship());
      }
    }
    LOG.debug("Matched {} => {}", name.canonicalNameWithAuthorship(), m);
    return m;
  }
  
  /**
   * Does comparison by rank, author and nom code to pick real match from candidates
   */
  private NameMatch matchCandidates(Name query, final List<Name> candidates) {
    final boolean compareRank = query.getRank() != null && query.getRank() != Rank.UNRANKED;
    final boolean compareAuthorship = query.hasAuthorship();
    final boolean compareCode = query.getCode() != null;
    final String queryname = SciNameNormalizer.normalizedAscii(query.canonicalNameWithoutAuthorship());
    final String queryfullname = SciNameNormalizer.normalizedAscii(query.canonicalNameWithAuthorship());
    final String queryauthorship = Strings.nullToEmpty(SciNameNormalizer.normalizedAscii(query.authorshipComplete()));
    // calculate score by rank, nomCode & authorship
    // immediately filtering no matches with a negative score
    int bestScore = 0;
    final List<Name> matches = Lists.newArrayList();
    for (Name n : candidates) {
      // 0 to 5
      int score = 0;
      
      // make sure rank match up exactly if part of query
      if (compareRank && !match(query.getRank(), n.getRank())) {
        continue;
      }
      
      // make sure nom code match up exactly if part of query
      if (compareCode && !match(query.getCode(), n.getCode())) {
        continue;
      }
      
      // exact full name match: =5
      if (queryfullname.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.canonicalNameWithAuthorship()))) {
        score = 5;
        
      } else {
        // remove different authorships or
        // 0 for unknown match
        // +1 for equal authorships
        // +2 for exact equal authorship strings
        Equality aeq = compareAuthorship ? authComp.compare(query, n) : Equality.UNKNOWN;
        if (aeq == Equality.DIFFERENT) {
          continue;
        }
        
        if (queryauthorship.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.authorshipComplete()))) {
          score += 2;
        } else if (aeq == Equality.EQUAL) {
          score += 1;
        }
        
        // exact canonical name match: +1
        if (queryname.equalsIgnoreCase(SciNameNormalizer.normalizedAscii(n.canonicalNameWithoutAuthorship()))) {
          score += 1;
        }
      }
      bestScore = addOrRemove(score, n, bestScore, matches);
    }
    
    if (matches.isEmpty()) {
      return NameMatch.noMatch();
      
    } else if (matches.size() == 1) {
      return buildMatch(query, matches.get(0));
      
    } else {
      // multiple, ambiguous matches
      LOG.debug("Ambiguous match ({} hits) for {}", matches.size(), query.canonicalNameWithAuthorship());
      NameMatch m = new NameMatch();
      m.setType(MatchType.AMBIGUOUS);
      m.setAlternatives(matches);
      return m;
    }
  }
  
  /**
   * @return new best score
   */
  private int addOrRemove(int score, Name n, int bestScore, List<Name> matches) {
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
  
  
  private static NameMatch buildMatch(Name query, Name match) {
    NameMatch m = new NameMatch();
    m.setName(match);
    if (query.canonicalNameWithAuthorship().equalsIgnoreCase(match.canonicalNameWithAuthorship())) {
      m.setType(MatchType.EXACT);
    } else {
      m.setType(MatchType.VARIANT);
    }
    return m;
  }
  
  private Name insert(Name orig) {
    Name name = new Name(orig);
    // reset all other keys
    name.setVerbatimKey(null);
    name.setHomotypicNameId(null);
    name.setNameIndexId(null);
    name.setDatasetKey(datasetKey);
    name.setOrigin(Origin.NAME_MATCHING);
    name.setNomStatus(NomStatus.DOUBTFUL);
    name.setPublishedInId(null);
    name.setPublishedInPage(null);
    name.setCreatedBy(Users.MATCHER);
    name.setModifiedBy(Users.MATCHER);
    // add to index map, assigning a new NI id
    add(name);
    // insert into postgres dataset
    dao.create(name, Users.MATCHER);
    return name;
  }
  
  @Override
  public int size() {
    return counter.get();
  }
  
  @Override
  public void add(Name name) {
    // generate new id
    name.setId(idGen.next());
    addWithID(name);
  }
  
  private synchronized void addWithID(Name name) {
    String key = key(name);
    ArrayList<Name> group;
    if (store.containsKey(key)) {
      group = store.get(key);
      // remove previous version if itt already existed.
      // Note that if the scientificName changed the key is likely different !!!
      group.removeIf(ex -> ex.getId().equals(name.getId()));
    } else {
      group = new ArrayList<>(1);
    }
    group.add(name);
    store.put(key, group);
  }
  
  private static String key(Name n) {
    return SciNameNormalizer.normalize(n.getScientificName());
  }
  
  private static boolean match(NomCode c1, NomCode c2) {
    if (c1 == null || c2 == null) return true;
    return c1 == c2;
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
  public void close() throws Exception {
    LOG.info("Closing names index ...");
    store.close();
    LOG.info("Names index db closed");
  }
  
}
