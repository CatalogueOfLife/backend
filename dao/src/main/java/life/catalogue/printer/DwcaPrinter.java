package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.util.RankUtils;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.TermWriter;
import life.catalogue.dao.TaxonCounter;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;

public abstract class DwcaPrinter extends RowTermPrinter{
  static final List<Term> COLUMNS = List.of(
    DwcTerm.taxonID,
    DwcTerm.parentNameUsageID,
    DwcTerm.acceptedNameUsageID,
    DwcTerm.taxonomicStatus,
    DwcTerm.taxonRank,
    DwcTerm.scientificName,
    DwcTerm.scientificNameAuthorship
  );
  private DwcaPrinter(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct,
                      @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                      SqlSessionFactory factory, Writer writer, TabularFormat tabFormat) throws IOException {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer, tabFormat, DwcTerm.Taxon, COLUMNS);
  }

  public static class TSV extends DwcaPrinter{
    public TSV(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct,
               @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
               SqlSessionFactory factory, Writer writer) throws IOException {
      super(params, ranks, extinct, countRank, taxonCounter, factory, writer, TabularFormat.TSV);
    }

    @Override
    public void write(SimpleName sn) {
      super.write(sn);
    }
  }
  public static class CSV extends DwcaPrinter{
    public CSV(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct,
               @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
               SqlSessionFactory factory, Writer writer) throws IOException {
      super(params, ranks, extinct, countRank, taxonCounter, factory, writer, TabularFormat.CSV);
    }
  }

  @Override
  void write(SimpleName sn) {
    writeSimpleColumns(sn, tw);
  }

  static void writeSimpleColumns(SimpleName sn, TermWriter tw) {
    tw.set(DwcTerm.taxonID, sn.getId());
    if (sn.isSynonym()) {
      tw.set(DwcTerm.acceptedNameUsageID, sn.getParentId());
    } else {
      tw.set(DwcTerm.parentNameUsageID, sn.getParentId());
    }
    tw.set(DwcTerm.taxonomicStatus, sn.getStatus());
    tw.set(DwcTerm.taxonRank, sn.getRank());
    tw.set(DwcTerm.scientificName, sn.getName());
    tw.set(DwcTerm.scientificNameAuthorship, sn.getAuthorship());
  }
}
