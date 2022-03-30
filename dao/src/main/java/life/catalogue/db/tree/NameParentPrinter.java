package life.catalogue.db.tree;

import life.catalogue.api.model.SimpleName;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

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

  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  public NameParentPrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, synonyms, ranks, countRank, taxonCounter, factory, writer);
  }

  public void setParentName(@Nullable Rank parentName) {
    this.printParent = true;
    this.parentName = parentName;
  }

  public void setPrintAuthorship(boolean printAuthorship) {
    this.printAuthorship = printAuthorship;
  }

  protected void start(SimpleName u) throws IOException {
    if (printAuthorship) {
      writer.append(u.getLabel());
    } else {
      writer.append(u.getName());
    }
    if (printParent && !parents.isEmpty()) {
      SimpleName p = null;
      if (parentName == null || u.getStatus().isSynonym()) {
        p = parents.getLast();
      } else {
        for (var sn : parents) {
          if (parentName == sn.getRank()) {
            p = sn;
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

  @Override
  protected void end(SimpleName u) throws IOException {
    // nothing to do
  }
}
