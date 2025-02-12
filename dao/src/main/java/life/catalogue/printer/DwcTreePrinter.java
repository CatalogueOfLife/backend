package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.io.TermWriter;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.parser.TaxGroupParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.collections.ListUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DwcTreePrinter extends AbstractTreePrinter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcTreePrinter.class);
  private static final Term T_GROUP = UnknownTerm.build("https://terms.checklistbank.org/taxGroupFromName", false);
  private static final Term T_GROUP_ANALYZED = UnknownTerm.build("https://terms.checklistbank.org/taxGroup", false);
  protected TermWriter tw;
  protected boolean showTaxGroups;
  private static final TaxGroupAnalyzer tgAnalyzer = new TaxGroupAnalyzer();

  public DwcTreePrinter(TreeTraversalParameter params, @Nullable  Set<Rank> ranks, @Nullable Boolean extinct,
                         @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                         SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  /**
   * MUST be called before the printer accepted usages
   * @param showTaxGroups
   */
  public void initWriter(boolean showTaxGroups) throws IOException {
    this.showTaxGroups = showTaxGroups;
    tw = new TermWriter(new TabWriter(writer), DwcTerm.Taxon,
      ListUtils.union(DwcaPrinter.COLUMNS, showTaxGroups ? List.of(DwcTerm.higherClassification, T_GROUP, T_GROUP_ANALYZED) : List.of(DwcTerm.higherClassification))
    );
  }

  @Override
  protected void start(SimpleName sn) throws IOException {
    DwcaPrinter.writeSimpleColumns(sn, tw);
    List<SimpleName> cl = parents.stream().map(s -> s.sn).collect(Collectors.toList());
    tw.set(DwcTerm.higherClassification, cl.stream().map(SimpleName::getName).collect(Collectors.joining(";")));
    if (showTaxGroups) {
      try {
        var tg = TaxGroupParser.PARSER.parse(sn.getName());
        tw.set(T_GROUP, tg.orElse(null));
        var tga = tgAnalyzer.analyze(sn, cl);
        tw.set(T_GROUP_ANALYZED, tga);
      } catch (UnparsableException e) {
        LOG.warn("Failed to parse taxgroup from scientificName {}: {}", sn.getName(), e.getMessage());
      }
    }
    tw.next();
  }

  @Override
  protected void end(SimpleName u) throws IOException {
    // nada
  }

}
