package life.catalogue.printer;

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
public abstract class AbstractTreePrinter extends AbstractPrinter {
  protected final LinkedList<SimpleName> parents = new LinkedList<>();
  protected int level = 0;
  protected EVENT last;
  protected enum EVENT {START, END}

  /**
   * @param params main traversal parameter defining what to print
   * @param ranks set of ranks to include. Can be null or empty to include all
   * @param countRank the rank to be used when counting with the taxonCounter
   */
  public AbstractTreePrinter(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(true, params, ranks, countRank, taxonCounter, factory, writer);
  }

  @Override
  protected void postIter() throws IOException {
    // send final end signals
    while (!parents.isEmpty()) {
      SimpleName p = parents.removeLast();
      if (ranks.isEmpty() || ranks.contains(p.getRank())) {
        end(p);
        level--;
        last = EVENT.END;
      }
    }
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
        if (countRank != null && taxonCounter != null) {
          taxonCount = taxonCounter.count(DSID.of(params.getDatasetKey(), u.getId()), countRank);
        }
        start(u);
        level++;
        last = EVENT.START;
      }
      parents.add(u);
      
    } catch (IOException e) {
      throw new PrinterException(e);
    }
  }

  @Override
  protected final void print(SimpleName u) {
    // we dont use this method - we have overwritten accept instead!
  }

  protected abstract void start(SimpleName u) throws IOException;

  protected abstract void end(SimpleName u) throws IOException;

}
