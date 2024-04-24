package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.common.io.CsvWriter;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.io.TermWriter;
import life.catalogue.dao.TaxonCounter;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;

import javax.annotation.Nullable;

/**
 * Prints simple names as TSV or CSV rows.
 */
public abstract class RowTermPrinter extends AbstractPrinter {
  protected final TermWriter tw;

  public RowTermPrinter(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                        SqlSessionFactory factory, Writer writer, TabularFormat format, Term rowType, List<Term> columns
  ) throws IOException {
    super(false, params, ranks, extinct, countRank, taxonCounter, factory, writer);
    if (format == TabularFormat.CSV) {
      tw = new TermWriter(new CsvWriter(writer), rowType, columns);
    } else {
      tw = new TermWriter(new TabWriter(writer), rowType, columns);
    }
  }

  @Override
  public void print(SimpleName u) {
    try {
      write(u);
      tw.next();
    } catch (IOException e) {
      throw new PrinterException(e);
    }
  }

  abstract void write(SimpleName sn) throws IOException;
}
