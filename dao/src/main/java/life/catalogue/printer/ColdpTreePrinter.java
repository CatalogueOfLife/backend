package life.catalogue.printer;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.util.RankUtils;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.dao.TaxonCounter;

import org.apache.commons.collections.ListUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class ColdpTreePrinter extends TermTreePrinter {
  private static final List<ColdpTerm> CLTERMS = Arrays.stream(ColdpTerm.DENORMALIZED_RANKS).collect(Collectors.toList());

  public ColdpTreePrinter(TreeTraversalParameter params, @Nullable  Set<Rank> ranks, @Nullable Boolean extinct,
                          @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                          SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  @Override
  protected List<Term> buildWriterColumns() {
    var cols = new ArrayList<Term>();
    cols.addAll(ColdpPrinter.COLUMNS);
    cols.addAll(CLTERMS);
    return List.copyOf(cols);
  }

  @Override
  protected void writeRow(SimpleName sn, List<SimpleName> cl) {
    ColdpPrinter.writeSimpleColumns(sn, tw);
    for (var ht : cl) {
      if (RankUtils.RANK2COLDP.containsKey(ht.getRank())) {
        tw.set(RankUtils.RANK2COLDP.get(ht.getRank()), ht.getName());
      }
    }
  }
}
