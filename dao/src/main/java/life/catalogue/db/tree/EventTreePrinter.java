package life.catalogue.db.tree;

import life.catalogue.api.model.SimpleName;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Print an entire dataset in a nested SimpleName json array.
 */
public abstract class EventTreePrinter extends AbstractTreePrinter {
  protected final Writer writer;
  protected EVENT last;
  protected enum EVENT {START, END}

  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  protected EventTreePrinter(int datasetKey, Integer sectorKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, ranks, factory);
    this.writer = writer;
  }

  protected abstract void startEvent(SimpleName u) throws IOException;

  protected final void start(SimpleName u) throws IOException {
    startEvent(u);
    last = EVENT.START;
  }

  protected abstract void endEvent(SimpleName u) throws IOException;

  protected final void end(SimpleName u) throws IOException {
    endEvent(u);
    last = EVENT.END;
  }

  @Override
  public void flush() throws IOException {
    writer.flush();
  }
  
}
