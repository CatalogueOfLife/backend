package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print an entire dataset in a nested way using start/end calls similar to SAX
 */
public abstract class AbstractTreePrinter extends AbstractPrinter {
  protected final LinkedList<FilterSN> parents = new LinkedList<>();
  protected int level = 0;
  protected EVENT last;
  protected enum EVENT {START, END}

  /**
   * @param params main traversal parameter defining what to print
   * @param ranks set of ranks to include. Can be null or empty to include all
   * @param countRank the rank to be used when counting with the taxonCounter
   */
  public AbstractTreePrinter(TreeTraversalParameter params, Set<Rank> ranks, Boolean extinct, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(true, params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  static class FilterSN {
    final SimpleName sn;
    final boolean filtered;

    FilterSN(SimpleName sn, boolean filtered) {
      this.sn = sn;
      this.filtered = filtered;
    }
  }
  @Override
  protected void postIter() throws IOException {
    // send final end signals
    while (!parents.isEmpty()) {
      FilterSN p = parents.removeLast();
      if (!p.filtered) {
        end(p.sn);
        level--;
        last = EVENT.END;
      }
    }
  }

  @Override
  public final void accept(SimpleName u) {
    try {
      // send end signals
      while (!parents.isEmpty() && !parents.peekLast().sn.getId().equals(u.getParent())) {
        var p = parents.removeLast();
        if (!p.filtered) {
          end(p.sn);
          level--;
          last = EVENT.END;
        }
      }

      final boolean filtered = filter(u);
      if (!filtered) {
        counter.inc(u);
        if (countRank != null && taxonCounter != null) {
          taxonCount = taxonCounter.count(DSID.of(params.getDatasetKey(), u.getId()), countRank);
        }
        start(u);
        level++;
        last = EVENT.START;
      }
      parents.add(new FilterSN(u, filtered));
      
    } catch (IOException e) {
      throw new PrinterException(e);
    }
  }

  @Override
  protected boolean filter(SimpleName u) {
    return super.filter(u) ||
      // also filter out synonyms which a filtered parent in the printed tree.
      // This can e.g. happen with the extinct filter
      (u.isSynonym() && (parents.isEmpty() || parents.getLast().filtered));
  }

  @Override
  protected final void print(SimpleName u) {
    // we dont use this method - we have overwritten accept instead!
  }

  protected abstract void start(SimpleName u) throws IOException;

  protected abstract void end(SimpleName u) throws IOException;

}
