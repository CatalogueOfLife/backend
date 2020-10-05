package life.catalogue.exporter;

import life.catalogue.api.model.*;
import life.catalogue.db.tree.SimpleUsageTreePrinter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Set;

/**
 * Print an entire dataset in a nested SimpleName json array.
 */
public class HtmlExporterSimple extends SimpleUsageTreePrinter {
  private final HtmlWriter writer;

  private HtmlExporterSimple(int datasetKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, null, startID, ranks, factory);
    this.writer = new HtmlWriter(writer, factory, "html", DSID.of(datasetKey, startID));
  }

  public static HtmlExporterSimple subtree(int datasetKey, String rootID, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporterSimple(datasetKey, rootID, null, factory, writer);
  }

  public static HtmlExporterSimple subtree(int datasetKey, String rootID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    return new HtmlExporterSimple(datasetKey, rootID, ranks, factory, writer);
  }

  @Override
  public int print() throws IOException {
    level=1;
    writer.header();
    int count = super.print();
    writer.footer();
    return count;
  }

  protected void start(SimpleName u) throws IOException {
    if (u.getStatus().isSynonym()) {
      if (writer.info.getSynonyms() == null) {
        writer.info.setSynonyms(new ArrayList<>());
      }
      writer.info.getSynonyms().add(new Synonym(u));

    } else {
      writer.taxon();
      writer.setTaxon(level, new Taxon(u));
    }
  }

  protected void end(SimpleName u) throws IOException {
    if (writer.hasTaxon() && !u.getStatus().isSynonym() && writer.getLevel()==(level-1)) {
      writer.taxon();
    }
    if (!u.getStatus().isSynonym()) {
      writer.divClose();
    }
  }

  @Override
  protected void flush() throws IOException {
    writer.close();
  }
}
