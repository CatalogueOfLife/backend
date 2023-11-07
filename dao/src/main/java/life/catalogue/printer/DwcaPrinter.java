package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.dao.TaxonCounter;

import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

public abstract class DwcaPrinter extends RowTermPrinter{
  private DwcaPrinter(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer, TabularFormat tabFormat) throws IOException {
    super(params, ranks, countRank, taxonCounter, factory, writer, tabFormat, ColdpTerm.NameUsage, List.of(
      ColdpTerm.ID,
      ColdpTerm.parentID,
      ColdpTerm.status,
      ColdpTerm.rank,
      ColdpTerm.scientificName,
      ColdpTerm.authorship
    ));
  }

  static public class TSV extends DwcaPrinter{
    public TSV(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) throws IOException {
      super(params, ranks, countRank, taxonCounter, factory, writer, TabularFormat.TSV);
    }
  }
  static public class CSV extends DwcaPrinter{
    public CSV(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) throws IOException {
      super(params, ranks, countRank, taxonCounter, factory, writer, TabularFormat.CSV);
    }
  }

  @Override
  void write(SimpleName sn) {
    tw.set(ColdpTerm.ID, sn.getId());
    tw.set(ColdpTerm.parentID, sn.getParentId());
    tw.set(ColdpTerm.status, sn.getStatus());
    tw.set(ColdpTerm.rank, sn.getRank());
    tw.set(ColdpTerm.scientificName, sn.getName());
    tw.set(ColdpTerm.authorship, sn.getAuthorship());
  }
}
