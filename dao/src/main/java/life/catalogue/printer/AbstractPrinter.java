package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.concurrent.UsageCounter;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Print an entire dataset in a nested way using start/end calls similar to SAX
 */
public abstract class AbstractPrinter implements Consumer<SimpleName>, AutoCloseable {
  protected final UsageCounter counter = new UsageCounter();
  protected final Writer writer;
  // sql tree traversal
  protected final TreeTraversalParameter params;
  // filter
  protected final Set<Rank> ranks;
  protected final Boolean extinct;
  // optional counts in results
  protected int taxonCount; // count
  protected final Rank countRank; // count by
  protected final TaxonCounter taxonCounter; // method to do the counting
  protected final SqlSessionFactory factory;
  protected SqlSession session;
  protected final boolean ordered;

  /**
   * @param ordered if true does a more expensive depth first traversal with ordered children
   * @param params main traversal parameter defining what to print
   * @param ranks set of ranks to include. Can be null or empty to include all
   * @param countRank the rank to be used when counting with the taxonCounter
   */
  public AbstractPrinter(boolean ordered, TreeTraversalParameter params, Set<Rank> ranks, Boolean extinct, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    this.ordered = ordered;
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
    this.extinct = extinct;
    this.countRank = countRank;
    this.taxonCounter = taxonCounter;
  }

  /**
   * @return number of written lines, i.e. name usages
   * @throws IOException
   */
  public int print() throws IOException {
    counter.clear();
    try {
      session = factory.openSession(true);
      if (ordered || params.hasFilter()) {
        PgUtils.consume(() -> session.getMapper(NameUsageMapper.class).processTreeSimple(params, ordered, ordered), this);
      } else {
        PgUtils.consume(() -> session.getMapper(NameUsageMapper.class).processDatasetSimple(params.getDatasetKey()), this);
      }
      postIter();
    } finally {
      close();
      session.close();
    }
    return counter.size();
  }

  @Override
  public void accept(SimpleName u) {
    if (!filter(u)) {
      counter.inc(u);
      if (countRank != null && taxonCounter != null) {
        taxonCount = taxonCounter.count(DSID.of(params.getDatasetKey(), u.getId()), countRank);
      }
      print(u);
    }
  }

  protected boolean filter(SimpleName u) {
    return (!ranks.isEmpty() && !ranks.contains(u.getRank())) ||
      (extinct != null && u.isExtinct() != extinct);
  }
  protected abstract void print(SimpleName u);

  /**
   * Override to implement a routine being called after the iteration is over, but with an open session and writer still.
   */
  protected void postIter() throws IOException {
    // nothing by default
  }

  public UsageCounter getCounter() {
    return counter;
  }

  @Override
  public void close() throws IOException {
    writer.flush();
  }

}
