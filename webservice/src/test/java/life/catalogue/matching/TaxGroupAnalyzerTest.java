package life.catalogue.matching;

import life.catalogue.api.vocab.TaxGroup;

import life.catalogue.matching.TaxGroupAnalyzer;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import static life.catalogue.api.model.SimpleName.sn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TaxGroupAnalyzerTest {

  TaxGroupAnalyzer analyzer = new TaxGroupAnalyzer();

  @Test
  public void detectCodeFromAuthorship() {
    assertEquals(Optional.empty(), TaxGroupAnalyzer.detectCodeFromAuthorship(sn("Puma")));
    assertEquals(Optional.empty(), TaxGroupAnalyzer.detectCodeFromAuthorship(sn("Puma", "Miller")));
    assertEquals(Optional.empty(), TaxGroupAnalyzer.detectCodeFromAuthorship(sn("Puma", "Mill.")));
    assertEquals(Optional.empty(), TaxGroupAnalyzer.detectCodeFromAuthorship(sn("Puma", "Miller & Joseph")));

    assertEquals(NomCode.ZOOLOGICAL, TaxGroupAnalyzer.detectCodeFromAuthorship(sn("Foo", "Miller, 1973")).get());
    assertEquals(NomCode.BOTANICAL, TaxGroupAnalyzer.detectCodeFromAuthorship(sn("Foo", "(Mill.) L.)")).get());
  }

  @Test
  public void detectCodeFromRank() {
    for (var r : Rank.LINNEAN_RANKS) {
      assertEquals(Optional.empty(), TaxGroupAnalyzer.detectCodeFromRank(sn(r, "Puma")));
    }
    assertEquals(NomCode.ZOOLOGICAL, TaxGroupAnalyzer.detectCodeFromRank(sn(Rank.EPIFAMILY, "Foo")).get());
    assertEquals(NomCode.ZOOLOGICAL, TaxGroupAnalyzer.detectCodeFromRank(sn(Rank.MORPH, "Foo")).get());
    assertEquals(NomCode.BACTERIAL, TaxGroupAnalyzer.detectCodeFromRank(sn(Rank.PHAGOVAR, "Foo")).get());
    assertEquals(NomCode.CULTIVARS, TaxGroupAnalyzer.detectCodeFromRank(sn(Rank.CULTIVAR, "Foo")).get());
  }

  @Test
  public void detectCodeFromSuffix() {
    assertEquals(Optional.empty(), TaxGroupAnalyzer.detectGroupFromSuffix(sn("Puma")));
    assertEquals(Optional.empty(), TaxGroupAnalyzer.detectGroupFromSuffix(sn("Fooideae")));
    assertEquals(new TaxGroup[]{TaxGroup.Animals}, TaxGroupAnalyzer.detectGroupFromSuffix(sn(Rank.FAMILY, "Fooidae")).get());
    assertEquals(Optional.empty(), TaxGroupAnalyzer.detectGroupFromSuffix(sn(Rank.ORDER, "Fooidae")));
  }

  @Test
  public void detectCode() {
    assertNull(analyzer.detectCode(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"))));
    assertNull(analyzer.detectCode(sn("Abies alba"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"))));
    assertEquals(NomCode.BOTANICAL, analyzer.detectCode(sn("Abies alba", "(L.) Mill."), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"))));
    assertEquals(NomCode.ZOOLOGICAL, analyzer.detectCode(sn("Abies alba", "Miller"), List.of(sn("Animalia"), sn("Vertebrata"), sn(Rank.EPIFAMILY, "Felidae"))));
  }

  @Test
  public void analyze() {
    assertEquals(TaxGroup.Animals, analyzer.analyze(sn("Animalia")));

    assertEquals(TaxGroup.Mammals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"))));
    assertEquals(TaxGroup.Mammals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn(Rank.FAMILY, "Felidae"))));
    assertEquals(TaxGroup.Mammals, analyzer.analyze(sn("Mammalia")));
    assertEquals(TaxGroup.Mammals, analyzer.analyze(sn(Rank.CLASS, "Mammalia")));

    // conflicting classifications, but resolvable
    assertEquals(TaxGroup.Animals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Plantae"), sn("Felidae"))));
    assertEquals(TaxGroup.Animals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"), sn("Coleoptera"))));

    // conflicting classifications, unresolvable
    assertNull(analyzer.analyze(sn("Animalia"), List.of(sn("Plantae"))));
  }

}