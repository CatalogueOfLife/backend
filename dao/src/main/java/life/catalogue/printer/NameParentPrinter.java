package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print all names with their parent name into a text file, one into each line.
 * The printer can optionally be configured to use as the parent name for accepted names either:
 *  - the direct parent at whatever rank (default)
 *  - a specific rank given for all names
 * Synonyms, if included, will always have the accepted name as the parent.
 */
public class NameParentPrinter extends AbstractTreePrinter {
  private boolean printAuthorship = true;
  private boolean printParent = false;
  private Rank parentName;
  private Predicate<SimpleName> filter;

  public NameParentPrinter(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  public void setParentName(@Nullable Rank parentName) {
    this.printParent = true;
    this.parentName = parentName;
  }

  public void setPrintAuthorship(boolean printAuthorship) {
    this.printAuthorship = printAuthorship;
  }

  /**
   * Sets an optional custom exclusion filter
   * @param filter predicate that excludes a name from being printed if it matches
   */
  public void setFilter(Predicate<SimpleName> filter) {
    this.filter = filter;
  }

  protected void start(SimpleName u) throws IOException {
    if (filter != null && filter.test(u)) {
      counter.dec(u);
    } else {

      if (printAuthorship) {
        writer.append(u.getLabel());
      } else {
        writer.append(u.getName());
      }
      if (printParent && !parents.isEmpty()) {
        SimpleName p = null;
        if (parentName == null || u.getStatus().isSynonym()) {
          p = parents.getLast().sn;
        } else {
          for (var sn : parents) {
            if (parentName == sn.sn.getRank()) {
              p = sn.sn;
              break;
            }
          }
        }
        if (p != null) {
          writer.append(" >> ");
          writer.append(p.getName());
        }
      }
      writer.append('\n');
    }
  }

  @Override
  protected void end(SimpleName u) throws IOException {
    // nothing to do
  }
}
