package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameUsageBaseTest {

  @Test
  public void abbreviate() {
    assertEquals("Sparrman, 1789", NameUsageBase.abbreviate("Sparrman, Andreas. 1789. Museum Carlsonianum, in quo novas et selectas aves, coloribus ad vivum brevique descriptione illustratas, suasu et sumtibus generosissimi possessoris. Typographia Regia, Holmiae. Vol. fasc. 4: pls.76-100."));
    assertEquals("Favret, 2018", NameUsageBase.abbreviate("Favret C. (2018). Aphid Species File (version 5.0, Dec 2017)."));
    assertEquals("von Thiele", NameUsageBase.abbreviate("von Thiele Schwarz U. bla bla bla"));
    assertEquals("MolluscaBase, 2016", NameUsageBase.abbreviate("MolluscaBase (2016). Accessed at http://www.molluscabase.org on 2016-12-07"));
    assertEquals("Söderström, 2015", NameUsageBase.abbreviate("Söderström L., Hagborg A., von Konrat M. & al. (2015). World checklist of hornworts and  liverworts. PhytoKeys 59: 1–828. doi: 10.3897/phytokeys.59.6261"));
    assertEquals("Ping-Ping, 2008", NameUsageBase.abbreviate( "Ping-Ping, Chen, Nico Nieser, and Ivor Lansbury. Notes on aquatic and semiaquatic bugs (Hemiptera: Heteroptera: Nepomorpha, Gerromorpha) from Malesia with description of three new species. Acta Entomologica Musei Nationalis Pragae, vol. 48, no. 2. (2008)"));
    assertEquals("Møller, 2000", NameUsageBase.abbreviate("Møller, Nils. A new species of Tetraripis from Thailand, with a critical assessment of the generic classification of the subfamily Rhagoveliinae (Hemiptera, Gerromorpha, Veliidae). Tijdschrift voor Entomologie, vol. 142, no. 2. (2000)"));
    assertEquals("A new species, 2000", NameUsageBase.abbreviate("A new species of Tetraripis from Thailand, with a critical assessment of the generic classification of the subfamily Rhagoveliinae (Hemiptera, Gerromorpha, Veliidae). Tijdschrift voor Entomologie, vol. 142, no. 2. (2000)"));
    assertEquals("ANewSpeciesOfTetrari…, 1888", NameUsageBase.abbreviate("ANewSpeciesOfTetraripisFromThailandWithACriticalAssessmentOfTheGenericClassification (1888) yeahyeah"));
  }
}