package org.col.admin.matching;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.matching.authorship.AuthorComparator;
import org.col.api.model.Name;
import org.col.common.kryo.ApiKryoFactory;
import org.col.common.mapdb.MapDbObjectSerializer;
import org.col.common.tax.SciNameNormalizer;
import org.col.db.dao.NameDao;
import org.col.db.mapper.NameMapper;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NameMatching implementation that is backed by a mapdb with a list of names keyed to their normalised
 * canonical name using the SciNameNormalizer.normalize() method.
 */
public class NameIndexMapDB implements NameIndex {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexMapDB.class);

  private final DB db;
  private final KryoPool pool;
  private final Map<String, NameList> names;
  private final AuthorComparator authComp;
  private final int datasetKey;
  private final SqlSessionFactory sqlFactory;


  static class NameList extends ArrayList<Name> {
    NameList() {
      super(1);
    }
    NameList(int initialCapacity) {
      super(initialCapacity);
    }
  }
  static class NameIndexKryoFactory extends ApiKryoFactory {
    @Override
    public Kryo create() {
      Kryo kryo = super.create();
      kryo.register(NameList.class);
      return kryo;
    }
  }


  /**
   * @param dbMaker
   * @param authComp
   * @param datasetKey the dataset the names index is stored in
   * @param sqlFactory sql session factory to talk to the data store backend if needed for inserts or initial loading
   */
  public NameIndexMapDB(DBMaker.Maker dbMaker, AuthorComparator authComp, int datasetKey, SqlSessionFactory sqlFactory) {
    this.db = dbMaker.make();
    this.authComp = Preconditions.checkNotNull(authComp);
    this.datasetKey = datasetKey;
    this.sqlFactory = Preconditions.checkNotNull(sqlFactory);

    pool = new KryoPool.Builder(new NameIndexKryoFactory())
        .softReferences()
        .build();
    names = db.hashMap("names")
        .keySerializer(Serializer.STRING_ASCII)
        .valueSerializer(new MapDbObjectSerializer<>(NameList.class, pool, 128))
        .counterEnable()
        //.valueInline()
        //.valuesOutsideNodesEnable()
        .createOrOpen();

    if (names.size() == 0) {
      loadFromPg();
    }
  }

  private void loadFromPg(){
    LOG.info("Loading names from postgres into names index");
    try (SqlSession s = sqlFactory.openSession()) {
      NameMapper mapper = s.getMapper(NameMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      ResultHandler<Name> handler = new ResultHandler<Name>() {
        @Override
        public void handleResult(ResultContext<? extends Name> ctx) {
          add(ctx.getResultObject());
          if (counter.incrementAndGet() % 1000 == 0) {
            LOG.debug("Added {} names", counter.get());
          }
        }
      };
      mapper.processDataset(datasetKey, handler);
      LOG.info("Loaded {} names from postgres into names index", counter.get());
    }
  }

  @Override
  public NameMatch match(Name name, boolean allowInserts, boolean verbose) {
    NameMatch m;
    NameList candidates = names.get(key(name));
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
        LOG.debug("Do not insert {} with ambiguous name matches into the names index", name.canonicalNameComplete());
      } else {
        insert(name);
        m.setName(name);
        m.setType(MatchType.INSERTED);
      }
    }
    return m;
  }

  /**
   * Does comparison by rank, author and nom code to pick real match from candidates
   */
  private NameMatch matchCandidates(Name query, final NameList candidates) {
    final boolean compareRank = query.getRank() != null && query.getRank() != Rank.UNRANKED;
    final boolean compareAuthorship = query.hasAuthorship();
    final boolean compareCode = query.getCode() != null;
    // filter by rank, nomCode & authorship
    List<Name> matches = new ArrayList<>(candidates);
    Iterator<Name> iter = matches.iterator();
    while (iter.hasNext()) {
      Name n = iter.next();
      // by rank
      if (compareRank && !match(query.getRank(), n.getRank())) {
        iter.remove();

      // by authorship
      } else if (compareAuthorship && authComp.compare(query, n) == Equality.DIFFERENT) {
        iter.remove();

      // nom code
      } else if (compareCode && !match(query.getCode(), n.getCode())) {
        iter.remove();
      }
    }

    if (matches.isEmpty()) {
      return NameMatch.noMatch();

    } else if (matches.size() == 1) {
      return buildMatch(query, matches.get(0));

    } else {
      // prefer exact matches
      Name exact = exactMatch(query, matches);
      if (exact != null) {
        LOG.debug("{} matches, but only 1 exact match {} for {}", matches.size(), exact.getKey(), query.canonicalName());
        return buildMatch(query, exact);
      }
      // still more matches, TODO: remove deleted ones and try again with exact match
      LOG.debug("Ambiguous match ({} hits) for {}", matches.size(), query.canonicalNameComplete());
      NameMatch m = new NameMatch();
      m.setType(MatchType.AMBIGUOUS);
      m.setAlternatives(matches);
      return m;
    }
  }

  private static NameMatch buildMatch(Name query, Name match) {
    NameMatch m = new NameMatch();
    m.setName(match);
    if (query.canonicalName().equalsIgnoreCase(match.canonicalName())) {
      m.setType(MatchType.EXACT);
    } else {
      m.setType(MatchType.VARIANT);
    }
    return m;
  }

  /**
   * Checks candidates for a single unambigous exact match
   */
  private Name exactMatch(Name query, List<Name> candidates) {
    Name match = null;
    for (Name cn : candidates) {
      if (query.canonicalName().equalsIgnoreCase(cn.canonicalName())) {
        // did we have a match already?
        if (match != null) {
          return null;
        }
        // no, keep it
        match = cn;
      }
    }
    return match;
  }

  private Name insert(Name name) {
    // reset all keys
    name.setKey(null);
    name.setId(null);
    name.setVerbatimKey(null);
    name.setHomotypicNameKey(null);
    name.setScientificNameID(null);
    name.setDatasetKey(datasetKey);
    // insert into postgres dataset
    //TODO: consider to make this async and collect for batch inserts
    try (SqlSession s = sqlFactory.openSession()) {
      NameDao dao = new NameDao(s);
      // this creates now a persistent names index key that will never be removed!
      dao.create(name);
      s.commit();
    }
    // add to index map
    add(name);
    return name;
  }

  @Override
  public int size() {
    return names.size();
  }

  @Override
  public void add(Name name) {
    String key = key(name);
    NameList group;
    if (names.containsKey(key)) {
      group = names.get(key);
    } else {
      group = new NameList(1);
    }
    group.add(name);
    names.put(key, group);
  }

  @Override
  public void addAll(Iterable<Name> names) {
    for (Name n : names) {
      add(n);
    }
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
    db.close();
  }

}
