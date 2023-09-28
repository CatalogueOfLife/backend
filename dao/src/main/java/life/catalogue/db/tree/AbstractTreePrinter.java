package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.concurrent.UsageCounter;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print an entire dataset in a nested way using start/end calls similar to SAX
 */
public abstract class AbstractTreePrinter implements Consumer<SimpleName>, AutoCloseable {
  private final UsageCounter counter = new UsageCounter();
  protected final Writer writer;
  protected final TreeTraversalParameter params;
  protected final Set<Rank> ranks;
  protected final Rank countRank;
  protected final TaxonCounter taxonCounter;
  protected final SqlSessionFactory factory;
  protected SqlSession session;
  protected final LinkedList<SimpleName> parents = new LinkedList<>();
  protected int level = 0;
  protected int taxonCount;
  protected boolean exhausted;
  protected EVENT last;
  protected enum EVENT {START, END}

  /**
   * @param params main traversal parameter defining what to print
   * @param ranks set of ranks to include. Can be null or empty to include all
   * @param countRank the rank to be used when counting with the taxonCounter
   */
  public AbstractTreePrinter(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    this.writer = writer;
    this.factory = factory;
    this.params = params;
    if (ranks != null) {
      this.ranks = ranks;
      this.params.setLowestRank(RankUtils.lowestRank(ranks));
    } else if (params.getLowestRank() != null){
      this.ranks = Arrays.stream(Rank.values()).filter(r -> r.ordinal() <= params.getLowestRank().ordinal() || r == Rank.UNRANKED).collect(Collectors.toSet());
    } else {
      this.ranks = Collections.EMPTY_SET;
    }
    this.countRank = countRank;
    this.taxonCounter = taxonCounter;
  }

  Cursor<SimpleName> iterate() {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    return num.processTreeSimple(params, true, true);
  }

  /**
   * @return number of written lines, i.e. name usages
   * @throws IOException
   */
  public int print() throws IOException {
    counter.clear();
    try {
      session = factory.openSession(true);
      PgUtils.consume(this::iterate, this);
      exhausted = true;
      // send final end signals
      while (!parents.isEmpty()) {
        SimpleName p = parents.removeLast();
        if (ranks.isEmpty() || ranks.contains(p.getRank())) {
          end(p);
          level--;
          last = EVENT.END;
        }
      }

    } finally {
      close();
      session.close();
    }
    return counter.size();
  }

  public UsageCounter getCounter() {
    return counter;
  }

  @Override
  public final void accept(SimpleName u) {
    try {
      // send end signals
      while (!parents.isEmpty() && !parents.peekLast().getId().equals(u.getParent())) {
        SimpleName p = parents.removeLast();
        if (ranks.isEmpty() || ranks.contains(p.getRank())) {
          end(p);
          level--;
          last = EVENT.END;
        }
      }
      if (ranks.isEmpty() || ranks.contains(u.getRank())) {
        counter.inc(u);
        if (countRank != null) {
          taxonCount = taxonCounter.count(DSID.of(params.getDatasetKey(), u.getId()), countRank);
        }
        start(u);
        level++;
        last = EVENT.START;
      }
      parents.add(u);
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract void start(SimpleName u) throws IOException;

  protected abstract void end(SimpleName u) throws IOException;

  @Override
  public void close() throws IOException {
    writer.flush();
  }

}
