package life.catalogue.common.tax;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class SciNameNormalizerTest {
  @Test
  public void removeHybridMarker() throws Exception {
    assertEquals("Abies", SciNameNormalizer.removeHybridMarker("Abies"));
    assertEquals("Abies", SciNameNormalizer.removeHybridMarker("×Abies"));
    assertEquals("Carex cayouettei", SciNameNormalizer.removeHybridMarker("Carex ×cayouettei"));
    assertEquals("Platanus hispanica", SciNameNormalizer.removeHybridMarker("Platanus x hispanica"));
  }

  @Test
  public void testNormalize() throws Exception {
    assertEquals("", SciNameNormalizer.normalize(""));
    assertEquals("Abies", SciNameNormalizer.normalize("Abies "));
    assertEquals("Abiies", SciNameNormalizer.normalize("Abiies "));
    assertEquals("Abyes", SciNameNormalizer.normalize("Abyes "));
    assertEquals("Abyes alb", SciNameNormalizer.normalize("Abyes  albus"));
    assertEquals("Abyes albiet", SciNameNormalizer.normalize("Abyes albieta"));
    assertEquals("Abies albiet", SciNameNormalizer.normalize("Abies albijeta"));
    assertEquals("Abies albiet", SciNameNormalizer.normalize("Abies albyeta"));
    assertEquals("Abies alb", SciNameNormalizer.normalize(" \txAbies × ållbbus\t"));

    assertEquals("Abies alb", SciNameNormalizer.normalize(" \txAbies × ållbbus\t"));
    assertEquals("Rhachis takt", SciNameNormalizer.normalize("Rhachis taktos"));

    assertEquals("Hieracium sabaud", SciNameNormalizer.normalize("Hieracium sabaudum"));
    assertEquals("Hieracium scorzoneraefoli", SciNameNormalizer.normalize("Hieracium scorzoneræfolium"));
    assertEquals("Hieracium scorzonerifoli", SciNameNormalizer.normalize("Hieracium scorzonerifolium"));
    assertEquals("Macrozamia platirach", SciNameNormalizer.normalize("Macrozamia platyrachis"));
    assertEquals("Macrozamia platirach", SciNameNormalizer.normalize("Macrozamia platyrhachis"));
    assertEquals("Cycas circinal", SciNameNormalizer.normalize("Cycas circinalis"));
    assertEquals("Cycas circinal", SciNameNormalizer.normalize("Cycas circinnalis"));
    assertEquals("Isolona perier", SciNameNormalizer.normalize("Isolona perrieri"));
    assertEquals("Isolona perier", SciNameNormalizer.normalize("Isolona perrierii"));

    assertEquals("Carex caiouet", SciNameNormalizer.normalize("Carex ×cayouettei"));
    assertEquals("Platanus hispanic", SciNameNormalizer.normalize("Platanus x hispanica"));
    // https://github.com/gbif/checklistbank/issues/7
    assertEquals(SciNameNormalizer.normalize("Eragrostis brownii"), SciNameNormalizer.normalize("Eragrostis brownei"));
    assertEquals("Eragrostis brown", SciNameNormalizer.normalize("Eragrostis brownei"));
    assertEquals("Theridion uhlig", SciNameNormalizer.normalize("Theridion uhlighi"));
    assertEquals("Theridion uhlig", SciNameNormalizer.normalize("Theridion uhliigi"));

    // all epithets of a trinomial should be stemmed
    assertEquals("Lynx ruf baile", SciNameNormalizer.normalize("Lynx rufus baileii"));
    assertEquals("Lynx ruf baile", SciNameNormalizer.normalize("Lynx rufus baileyi"));

    assertEquals("Larus fusc fusc", SciNameNormalizer.normalize("Larus fuscus fusca"));
    assertEquals("Eragrostis brown brown", SciNameNormalizer.normalize("Eragrostis brownei brownii"));
    assertEquals("Larus fusc Miller", SciNameNormalizer.normalize("Larus fuscus Miller"));

    assertEquals("Poecile montan afin", SciNameNormalizer.normalize("Poecile montanus affinis"));
    assertEquals("Poecile montan afin", SciNameNormalizer.normalize("Poecile montana affinis"));
    assertEquals("Poecile montan afin", SciNameNormalizer.normalize("Poecile montana affina"));
    assertEquals("Poecile montan afin", SciNameNormalizer.normalize("Poecile montanus affinus"));
  }

  @Test
  public void testNormalizeAll() throws Exception {
    assertEquals("", SciNameNormalizer.normalizeAll(""));
    assertEquals("Abies", SciNameNormalizer.normalizeAll("Abies "));
    assertEquals("Abies", SciNameNormalizer.normalizeAll("Abiies "));
    assertEquals("Abies", SciNameNormalizer.normalizeAll("Abyes "));
    assertEquals("Abies alb", SciNameNormalizer.normalizeAll("Abyes  albus"));
    assertEquals("Abies albiet", SciNameNormalizer.normalizeAll("Abyes albieta"));
    assertEquals("Abies albiet", SciNameNormalizer.normalizeAll("Abies albijeta"));
    assertEquals("Abies albiet", SciNameNormalizer.normalizeAll("Abies albyeta"));
    assertEquals("Abies alb", SciNameNormalizer.normalizeAll(" \txAbies × ållbbus\t"));

    assertEquals("Abies alb", SciNameNormalizer.normalizeAll(" \txAbies × ållbbus\t"));
    assertEquals("Rachis takt", SciNameNormalizer.normalizeAll("Rhachis taktos"));

    assertEquals("Hieracium sabaud", SciNameNormalizer.normalizeAll("Hieracium sabaudum"));
    assertEquals("Hieracium scorzoneraefoli", SciNameNormalizer.normalizeAll("Hieracium scorzoneræfolium"));
    assertEquals("Hieracium scorzonerifoli", SciNameNormalizer.normalizeAll("Hieracium scorzonerifolium"));
    assertEquals("Macrozamia platirach", SciNameNormalizer.normalizeAll("Macrozamia platyrachis"));
    assertEquals("Macrozamia platirach", SciNameNormalizer.normalizeAll("Macrozamia platyrhachis"));
    assertEquals("Cicas circinal", SciNameNormalizer.normalizeAll("Cycas circinalis"));
    assertEquals("Cicas circinal", SciNameNormalizer.normalizeAll("Cycas circinnalis"));
    assertEquals("Isolona perier", SciNameNormalizer.normalizeAll("Isolona perieri"));
    assertEquals("Isolona perier", SciNameNormalizer.normalizeAll("Isolona perrieri"));
    assertEquals("Isolona perier", SciNameNormalizer.normalizeAll("Isolona perrierii"));

    assertEquals("Carex caiouet", SciNameNormalizer.normalizeAll("Carex ×cayouettei"));
    assertEquals("Platanus hispanic", SciNameNormalizer.normalizeAll("Platanus x hispanica"));
    // https://github.com/gbif/checklistbank/issues/7
    assertEquals("Eragrostis brown", SciNameNormalizer.normalizeAll("Eragrostis brownii"));
    assertEquals("Eragrostis brown", SciNameNormalizer.normalizeAll("Eragrostis brownei"));
  }

  @Test
  public void testHybridCross() throws Exception {
    assertEquals("xcayouettei", SciNameNormalizer.normalize("xcayouettei"));
    assertEquals("cayouettei", SciNameNormalizer.normalize("×cayouettei"));

    assertEquals("Carex xcaiouet", SciNameNormalizer.normalize("Carex xcayouettei"));
    assertEquals("Carex caiouet", SciNameNormalizer.normalize("Carex ×cayouettei"));
    assertEquals("Carex caiouet", SciNameNormalizer.normalize("×Carex cayouettei"));
    assertEquals("Carex xcaiouet", SciNameNormalizer.normalize("xCarex xcayouettei"));
    assertEquals("Carex caiouet", SciNameNormalizer.normalize("XCarex cayouettei"));
    assertEquals("Carex caiouet", SciNameNormalizer.normalize("xCarex ×cayouettei"));
    assertEquals("Carex caiouet", SciNameNormalizer.normalize("xCarex ×caiouettei"));

    assertEquals("Platanus hispanic", SciNameNormalizer.normalize("Platanus x hispanica"));

  }

  @Test
  public void testNonAscii() throws Exception {
    assertEquals("Cem Andrexi", SciNameNormalizer.normalize("Çem Ándrexï"));
    assertEquals("SOEZsoezY¥µAAAAAAAECEEEEIIIIDNOOOOOOUUUUYssaaaaaaaeceeeeiiiidnoooooouuuuyy", SciNameNormalizer.normalize("ŠŒŽšœžŸ¥µÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýÿ"));
  }

  @Test
  public void testEpithetStemming() throws Exception {
    assertEquals("", SciNameNormalizer.stemEpithet(""));

    assertEquals("alb", SciNameNormalizer.stemEpithet("alba"));
    assertEquals("alb", SciNameNormalizer.stemEpithet("albus"));
    assertEquals("alb", SciNameNormalizer.stemEpithet("album"));

    assertEquals("dentat", SciNameNormalizer.stemEpithet("dentata"));
    assertEquals("dentat", SciNameNormalizer.stemEpithet("dentatus"));
    assertEquals("dentat", SciNameNormalizer.stemEpithet("dentatum"));

    assertEquals("mult", SciNameNormalizer.stemEpithet("multi"));
    assertEquals("mult", SciNameNormalizer.stemEpithet("multae"));
    assertEquals("mult", SciNameNormalizer.stemEpithet("multa"));

    assertEquals("facil", SciNameNormalizer.stemEpithet("facilis"));
    assertEquals("facil", SciNameNormalizer.stemEpithet("facile"));

    assertEquals("dulc", SciNameNormalizer.stemEpithet("dulcis"));
    assertEquals("dulc", SciNameNormalizer.stemEpithet("dulce"));

    assertEquals("virid", SciNameNormalizer.stemEpithet("viridis"));
    assertEquals("virid", SciNameNormalizer.stemEpithet("viride"));

    assertEquals("capens", SciNameNormalizer.stemEpithet("capensis"));
    assertEquals("capens", SciNameNormalizer.stemEpithet("capense"));

    assertEquals("laniger", SciNameNormalizer.stemEpithet("laniger"));
    assertEquals("laniger", SciNameNormalizer.stemEpithet("lanigera"));
    assertEquals("laniger", SciNameNormalizer.stemEpithet("lanigerum"));

    assertEquals("cerifer", SciNameNormalizer.stemEpithet("cerifer"));
    assertEquals("cerifer", SciNameNormalizer.stemEpithet("cerifera"));
    assertEquals("cerifer", SciNameNormalizer.stemEpithet("ceriferum"));

    assertEquals("ruber", SciNameNormalizer.stemEpithet("ruber"));
    assertEquals("ruber", SciNameNormalizer.stemEpithet("rubra"));

    assertEquals("porifer", SciNameNormalizer.stemEpithet("porifera"));
    assertEquals("porifer", SciNameNormalizer.stemEpithet("porifer"));
    assertEquals("porifer", SciNameNormalizer.stemEpithet("poriferum"));

    assertEquals("niger", SciNameNormalizer.stemEpithet("niger"));
    assertEquals("niger", SciNameNormalizer.stemEpithet("nigra"));
    assertEquals("niger", SciNameNormalizer.stemEpithet("nigrum"));

    assertEquals("pulcher", SciNameNormalizer.stemEpithet("pulcher")); // should we stem cher too?
    assertEquals("pulcher", SciNameNormalizer.stemEpithet("pulchra"));
    assertEquals("pulcher", SciNameNormalizer.stemEpithet("pulchrum"));

    assertEquals("muliebr", SciNameNormalizer.stemEpithet("muliebris"));
    assertEquals("muliebr", SciNameNormalizer.stemEpithet("muliebre"));

    assertEquals("allb", SciNameNormalizer.stemEpithet("allbus"));
    assertEquals("alab", SciNameNormalizer.stemEpithet("alaba"));
    assertEquals("ala", SciNameNormalizer.stemEpithet("alaus"));
    assertEquals("ala", SciNameNormalizer.stemEpithet("alaa"));

    assertEquals("pericul", SciNameNormalizer.stemEpithet("periculum"));
    assertEquals("pericul", SciNameNormalizer.stemEpithet("periculi"));
    assertEquals("pericul", SciNameNormalizer.stemEpithet("pericula"));

    assertEquals("viator", SciNameNormalizer.stemEpithet("viatrix"));
    assertEquals("viator", SciNameNormalizer.stemEpithet("viator"));
    assertEquals("viator", SciNameNormalizer.stemEpithet("viatoris"));

    assertEquals("nevadens", SciNameNormalizer.stemEpithet("nevadense"));
    assertEquals("nevadens", SciNameNormalizer.stemEpithet("nevadensis"));

    assertEquals("oriental", SciNameNormalizer.stemEpithet("orientale"));
    assertEquals("oriental", SciNameNormalizer.stemEpithet("orientalis"));

    // UNCHANGED
    assertEquals("ferox", SciNameNormalizer.stemEpithet("ferox"));
    assertEquals("simplex", SciNameNormalizer.stemEpithet("simplex"));
    assertEquals("elegans", SciNameNormalizer.stemEpithet("elegans"));
    assertEquals("pubescens", SciNameNormalizer.stemEpithet("pubescens"));
    // greek names stay as they are
    assertEquals("albon", SciNameNormalizer.stemEpithet("albon"));
  }
}
