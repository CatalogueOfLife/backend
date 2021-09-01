package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.RankedID;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.tax.RankUtils;
import life.catalogue.concurrent.UsageCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print an entire dataset in a nested way using start/end calls similar to SAX
 */
public abstract class AbstractTreePrinter<T extends RankedID> implements Consumer<T> {
  private final UsageCounter counter = new UsageCounter();
  protected final int datasetKey;
  protected final Integer sectorKey;
  protected final String startID;
  protected final boolean synonyms; // whether synonyms should be included or not
  protected final Set<Rank> ranks;
  protected final Rank lowestRank;
  protected final Rank countRank;
  protected final TaxonCounter taxonCounter;
  protected final SqlSessionFactory factory;
  protected SqlSession session;
  private final LinkedList<T> parents = new LinkedList<>();
  protected int level = 0;
  protected int taxonCount;
  protected boolean exhausted;

  protected AbstractTreePrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, SqlSessionFactory factory) {
    this(datasetKey, sectorKey, startID, synonyms, ranks, null, null, factory);
  }

  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   * @param countRank the rank to be used when counting with the taxonCounter
   */
  protected AbstractTreePrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.startID = startID;
    this.sectorKey = sectorKey;
    this.factory = factory;
    this.synonyms = synonyms;
    this.ranks = ObjectUtils.coalesce(ranks, Collections.EMPTY_SET);
    this.lowestRank = RankUtils.lowestRank(ranks);
    this.countRank = countRank;
    this.taxonCounter = taxonCounter;
  }

  abstract Cursor<T> iterate();

  abstract String getParentId(T usage);

  /**
   * @return number of written lines, i.e. name usages
   * @throws IOException
   */
  public int print() throws IOException {
    counter.clear();
    try {
      session = factory.openSession(true);
      iterate().forEach(this);
      exhausted = true;
      // send final end signals
      while (!parents.isEmpty()) {
        T p = parents.removeLast();
        if (ranks.isEmpty() || ranks.contains(p.getRank())) {
          end(p);
          level--;
        }
      }

    } finally {
      flush();
      session.close();
    }
    return counter.size();
  }

  public UsageCounter getCounter() {
    return counter;
  }

  @Override
  public final void accept(T u) {
    try {
      // send end signals
      while (!parents.isEmpty() && !parents.peekLast().getId().equals(getParentId(u))) {
        T p = parents.removeLast();
        if (ranks.isEmpty() || ranks.contains(p.getRank())) {
          end(p);
          level--;
        }
      }
      if (ranks.isEmpty() || ranks.contains(u.getRank())) {
        counter.inc(u);
        if (countRank != null) {
          taxonCount = taxonCounter.count(DSID.of(datasetKey, u.getId()), countRank);
        }
        start(u);
        level++;
      }
      parents.add(u);
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract void start(T u) throws IOException;

  protected abstract void end(T u) throws IOException;

  protected abstract void flush() throws IOException;

}
