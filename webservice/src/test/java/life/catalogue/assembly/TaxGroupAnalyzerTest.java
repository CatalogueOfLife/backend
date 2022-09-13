package life.catalogue.assembly;

import life.catalogue.api.model.SimpleName;

import life.catalogue.api.vocab.TaxGroup;

import org.junit.Test;

import static org.junit.Assert.*;

public class TaxGroupAnalyzerTest {

  @Test
  public void analyze() {
    TaxGroupAnalyzer analyzer = new TaxGroupAnalyzer();
    assertEquals(TaxGroup.Animals, analyzer.analyze(SimpleName.sn("Animalia")));
  }
}