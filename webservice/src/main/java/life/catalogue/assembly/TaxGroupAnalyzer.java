package life.catalogue.assembly;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.parser.TaxGroupParser;
import life.catalogue.parser.UnparsableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class TaxGroupAnalyzer {
  private static final Logger LOG = LoggerFactory.getLogger(TaxGroupAnalyzer.class);
  final TaxGroupParser parser = TaxGroupParser.PARSER;

  public TaxGroup analyze(SimpleName name) {
    return analyze(name, List.of());
  }

  public TaxGroup analyze(SimpleName name, Collection<? extends SimpleName> classification) {
    try {
      var pg = parser.parse(name.getName());
      if (pg.isPresent()) {
        return pg.get();
      }
      for (var sn : classification) {
        pg = parser.parse(sn.getName());
        if (pg.isPresent()) {
          return pg.get();
        }
      }
    } catch (UnparsableException e) {
      LOG.error("Error analyzing taxonomic group", e);
    }
    // TODO: code, authorship (abbreviations, years) or name suffix specific for codes to infer a group
    return null;
  }
}
