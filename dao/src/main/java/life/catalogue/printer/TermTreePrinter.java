package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.terms.ClbTerm;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.io.TermWriter;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.parser.TaxGroupParser;
import life.catalogue.parser.UnparsableException;

import org.apache.commons.collections.ListUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * A printer class that uses ordered tree traversals and offers the full classification to its subclasses
 * for printing rows of terms.
 */
abstract class TermTreePrinter extends AbstractTreePrinter {
  private static final Logger LOG = LoggerFactory.getLogger(TermTreePrinter.class);
  private boolean showTaxGroups = false;
  private static final TaxGroupAnalyzer tgAnalyzer = new TaxGroupAnalyzer();
  protected TermWriter tw;
  private List<SimpleName> rootClassification = Collections.emptyList();

  public TermTreePrinter(TreeTraversalParameter params, @Nullable  Set<Rank> ranks, @Nullable Boolean extinct,
                         @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                         SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  /**
   * If called, instructs the printer to include 2 more terms for the taxon group analyzer.
   */
  public void showTaxGroups() throws IOException {
    this.showTaxGroups = true;
  }

  /**
   * Sets a root classification to include with every record.
   * Expects the list to start with the highest, root taxon. You can supply the inverse ordering when you set the reverse flag to true.
   * This is needed when only a part of a dataset is printed and no information about the entire classification is found in the parents stack.
   */
  public void setRootClassification(List<SimpleName> classification, boolean reverse) throws IOException {
    if (classification == null) {
      rootClassification = Collections.emptyList();
    } else {
      var list = new ArrayList<>(classification);
      if (reverse) {
        Collections.reverse(list);
      }
      rootClassification = List.copyOf(list);
    }
  }

  /**
   * We postpone initialising the writer so we can add columns still once the constructor was called.
   */
  private void initWriter() throws IOException {
    var cols = ListUtils.union(buildWriterColumns(), showTaxGroups ? List.of(ClbTerm.taxGroupFromName, ClbTerm.taxGroup) : Collections.EMPTY_LIST);
    tw = new TermWriter(new TabWriter(writer), DwcTerm.Taxon, cols);
  }

  protected abstract List<Term> buildWriterColumns();

  @Override
  protected void start(SimpleName sn) throws IOException {
    if (tw == null) {
      initWriter();
    }
    final List<SimpleName> cl = new ArrayList<>();
    if (rootClassification != null && !rootClassification.isEmpty()) {
      cl.addAll(rootClassification);
    }
    cl.addAll(parents.stream().map(s -> s.sn).collect(Collectors.toList()));
    writeRow(sn, cl);
    if (showTaxGroups) {
      try {
        var tg = TaxGroupParser.PARSER.parse(sn.getName());
        tw.set(ClbTerm.taxGroupFromName, tg.orElse(null));
        var tga = tgAnalyzer.analyze(sn, cl);
        tw.set(ClbTerm.taxGroup, tga);
      } catch (UnparsableException e) {
        LOG.warn("Failed to parse taxgroup from scientificName {}: {}", sn.getName(), e.getMessage());
      }
    }
    tw.next();
  }

  abstract protected void writeRow(SimpleName sn, List<SimpleName> classification);

  @Override
  protected void end(SimpleName u) throws IOException {
    // nada
  }

  @Override
  public void close() throws IOException {
    if (tw == null) {
      initWriter();
    }
    super.close();
  }
}
