package life.catalogue.assembly;

import life.catalogue.api.model.SimpleName;

import life.catalogue.api.vocab.TaxGroup;

import org.junit.Test;

import java.util.List;
import static life.catalogue.api.model.SimpleName.sn;
import static org.junit.Assert.*;

public class TaxGroupAnalyzerTest {

  @Test
  public void analyze() {
    TaxGroupAnalyzer analyzer = new TaxGroupAnalyzer();
    assertEquals(TaxGroup.Animals, analyzer.analyze(sn("Animalia")));

    assertEquals(TaxGroup.Mammals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"))));
    assertEquals(TaxGroup.Mammals, analyzer.analyze(sn("Mammalia")));

    // conflicting classifications, but resolvable
    assertEquals(TaxGroup.Animals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Plantae"), sn("Felidae"))));
    assertEquals(TaxGroup.Animals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"), sn("Coleoptera"))));

    // conflicting classifications, unresolvable
    assertNull(analyzer.analyze(sn("Animalia"), List.of(sn("Plantae"))));
  }
}