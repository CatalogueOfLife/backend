package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.util.RankUtils;
import life.catalogue.dao.TaxonCounter;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;


public class DwcTreePrinter extends TermTreePrinter {

  public DwcTreePrinter(TreeTraversalParameter params, @Nullable  Set<Rank> ranks, @Nullable Boolean extinct,
                         @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                         SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  @Override
  protected List<Term> buildWriterColumns() {
    var cols = new ArrayList<Term>();
    cols.addAll(DwcaPrinter.COLUMNS);
    cols.addAll(RankUtils.CL_TERMS_DWC);
    cols.add(DwcTerm.higherClassification);
    return List.copyOf(cols);
  }

  @Override
  protected void writeRow(SimpleName sn, List<SimpleName> cl) {
    DwcaPrinter.writeSimpleColumns(sn, tw);
    for (var ht : cl) {
      if (RankUtils.RANK2DWC.containsKey(ht.getRank())) {
        tw.set(RankUtils.RANK2DWC.get(ht.getRank()), ht.getName());
      }
    }
    tw.set(DwcTerm.higherClassification, cl.stream().map(SimpleName::getName).collect(Collectors.joining(";")));
  }
}
