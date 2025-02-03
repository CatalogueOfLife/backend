package life.catalogue.matching;

import life.catalogue.api.vocab.TaxGroup;

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
    assertEquals(TaxGroup.Mammals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Plantae"), sn("Felidae"))));
    assertEquals(TaxGroup.Animals, analyzer.analyze(sn("Puma"), List.of(sn("Animalia"), sn("Vertebrata"), sn("Felidae"), sn("Coleoptera"))));

    // conflicting classifications, unresolvable
    assertEquals(TaxGroup.Eukaryotes, analyzer.analyze(sn("Animalia"), List.of(sn("Plantae"))));

    assertEquals(TaxGroup.Plants, analyzer.analyze(sn("Tracheophyta")));
    assertEquals(TaxGroup.Insects, analyzer.analyze(sn("Insecta")));

    assertNull(analyzer.analyze(sn(Rank.GENUS, "Dictymia", "Sm."), List.of(sn(Rank.KINGDOM, "incertae sedis"))));
  }

  /**
   * https://github.com/CatalogueOfLife/xcol/issues/146
   */
  @Test
  public void algae() {
    assertEquals(TaxGroup.Algae, analyzer.analyze(sn("Acrochaetium"),
      List.of(sn("Protista"), sn("Rhodophyta"), sn("Florideophyceae"), sn("Acrochaetiales"), sn("Acrochaetiaceae")))
    );
    assertEquals(TaxGroup.Algae, analyzer.analyze(sn("Acrochaetium"),
      List.of(sn("Biota"), sn("Plantae"), sn("Rhodophyta"), sn("Eurhodophytina"), sn("Florideophyceae"), sn("Acrochaetiales"), sn("Acrochaetiaceae")))
    );
  }

  /**
   * https://github.com/CatalogueOfLife/data/issues/913
   */
  @Test
  public void dino() {
    assertEquals(TaxGroup.Reptiles, analyzer.analyze(sn("Acherontisuchus guajiraensis"),
      List.of(
        sn("Life"), sn("Eucarya"), sn("Opisthokonta"), sn("Animalia"), sn("Bilateria"), sn("Eubilateria"), sn("Deuterostomia"), sn("Chordata"), sn("Vertebrata"), sn("Gnathostomata"), sn("Osteichthyes"), sn("Sarcopterygii"), sn("Dipnotetrapodomorpha"), sn("Tetrapodomorpha"), sn("Tetrapoda"), sn("Reptiliomorpha"), sn("Anthracosauria"), sn("Amphibiosauria"), sn("Cotylosauria"), sn("Amniota"), sn("Sauropsida"), sn("Reptilia"), sn("Eureptilia"), sn("Romeriida"), sn("Diapsida"), sn("Archosauromorpha"), sn("Crocopoda"), sn("Archosauriformes"), sn("Eucrocopoda"), sn("Archosauria"), sn("Pseudosuchia"), sn("Suchia"), sn("Paracrocodylomorpha"), sn("Loricata"), sn("Crocodylomorpha"), sn("Solidocrania"), sn("Crocodyliformes"), sn("Mesoeucrocodylia"), sn("Neosuchia"), sn("Coelognathosuchia"), sn("Tethysuchia"), sn("Dyrosauroidea"), sn("Dyrosauridae"), sn("Hyposaurinae"), sn("Acherontisuchus")
      )
    ));
  }

}