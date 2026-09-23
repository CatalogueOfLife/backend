package life.catalogue.matching;

import life.catalogue.api.model.*;

import org.gbif.nameparser.api.Authorship;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The pairs below are real: a COL26.9 name with its reference against the same name in the BHL dataset 310770,
 * see https://github.com/CatalogueOfLife/backend/issues/1606
 */
public class PublishedInIdentityTest {

  static Name name(String authorship, String page) {
    Name n = new Name();
    n.setAuthorship(authorship);
    n.setPublishedInPage(page);
    return n;
  }

  static Name name(String authorship, String year, String page) {
    Name n = name(authorship, page);
    n.setCombinationAuthorship(Authorship.yearAuthors(year, authorship));
    return n;
  }

  /**
   * A reference as COL typically has it, a free text citation with at most a parsed year
   */
  static Reference cit(String citation, Integer year) {
    Reference r = new Reference();
    r.setCitation(citation);
    r.setYear(year);
    if (year != null) {
      r.setCsl(new CslData());
      r.getCsl().setIssued(new CslDate(year));
    }
    return r;
  }

  static Reference cit(String citation, Integer year, String doi) {
    Reference r = cit(citation, year);
    if (r.getCsl() == null) {
      r.setCsl(new CslData());
    }
    r.getCsl().setDOI(doi);
    return r;
  }

  /**
   * A BHL item: a bound volume of a journal or a book, the title being the journal or book title
   */
  static Reference item(String title, String volume, int year, String doi, String url) {
    CslData csl = new CslData();
    csl.setTitle(title);
    csl.setVolume(volume);
    csl.setIssued(new CslDate(year));
    csl.setDOI(doi);
    csl.setURL(url);
    Reference r = new Reference();
    r.setCsl(csl);
    r.setYear(year);
    r.setCitation(title + ". (" + year + "). " + volume + ". " + url);
    return r;
  }

  /**
   * A BHL part: an article within a journal
   */
  static Reference part(String title, String container, String volume, int year, String doi, String url) {
    Reference r = item(title, volume, year, doi, url);
    r.getCsl().setContainerTitle(container);
    r.setCitation(title + ". (" + year + "). " + container + ", " + volume + ". " + url);
    return r;
  }

  static PublishedInIdentity.Verdict cmp(Name n1, Reference r1, Name n2, Reference r2) {
    return PublishedInIdentity.compare(n1, r1, n2, r2);
  }

  static void assertNothing(PublishedInIdentity.Verdict v) {
    assertFalse(v.contradicted());
    assertFalse(v.samePage());
    assertFalse(v.sameWork());
    assertFalse(v.stub());
    assertNull(v.doi());
  }

  @Test
  public void samePage() {
    // Aaroniella madecassa, the first example of #1606
    var v = cmp(
      name("Badonnel, 1967", "146"),
      cit("Badonnel, A. (1967) Insectes Psocoptères. Faune de Madagascar, 23, 1–235.", null),
      name("Badonnel, 1967", "146"),
      item("Faune de Madagascar", "Vol. 23 (1967), Insectes psocoptères", 1967, null, "https://www.biodiversitylibrary.org/item/296466")
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());
    // a volume is not the work a citation refers to
    assertFalse(v.sameWork());
    assertFalse(v.stub());
    assertNull(v.doi());
  }

  @Test
  public void pageFromCitation() {
    var v = cmp(
      name("Hammel & J.Gómez", null),
      cit("Hammel, & Gómez, J. (1994). Novon 4(4): 350 (1994).", 1994),
      name("Hammel & J.Gómez", "350"),
      part("New species in the Acanthaceae of Costa Rica", "Novon a Journal of Botanical Nomenclature from the Missouri Botanical Garden", "4", 1994, "10.2307/3391443", "https://www.biodiversitylibrary.org/part/42")
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());

    v = cmp(
      name("Schltr.", null),
      cit("Repert. Spec. Nov. Regni Veg. 10: 6 (1911)", null),
      name("Schltr.", "6"),
      item("Repertorium specierum novarum regni vegetabilis", "v.10 (1911-1912)", 1911, null, "https://www.biodiversitylibrary.org/item/7031")
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());

    v = cmp(
      name("Schltr.", null),
      cit("Repert. Spec. Nov. Regni Veg. 10: 6 (1911)", null),
      name("Schltr.", "8"),
      item("Repertorium specierum novarum regni vegetabilis", "v.10 (1911-1912)", 1911, null, "https://www.biodiversitylibrary.org/item/7031")
    );
    assertTrue(v.contradicted());
  }

  @Test
  public void pageWithinRange() {
    var v = cmp(
      name("Whitfield, 1985", null),
      cit("Whitfield, J.B. The Nearctic species of Deuterixys Mason (Hymenoptera: Braconidae). Pan-Pacific Entomologist. 61(1):60-67. (1985).", null),
      name("Whitfield, 1985", "65"),
      part("The Nearctic species of Deuterixys Mason (Hymenoptera: Braconidae)", "The Pan-Pacific Entomologist", "61(1)", 1985, null, "https://www.biodiversitylibrary.org/part/270160")
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());
    assertTrue(v.sameWork());

    // a page range on the name itself
    v = cmp(
      name("Gofas, 2010", "91-96"),
      cit("Gofas, S. (2010). A new Manzonia (Gastropoda: Rissoidae) from nothwestern Morocco. Iberus, 28(1): 91-96.", 2010),
      name("Gofas, 2010", "92"),
      part("A new Manzonia (Gastropoda: Rissoidae) from northwestern Morocco", "Iberus : Revista de la Sociedad Española de Malacología", "28", 2010, null, null)
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());

    v = cmp(
      name("Whitfield, 1985", null),
      cit("Whitfield, J.B. The Nearctic species of Deuterixys Mason (Hymenoptera: Braconidae). Pan-Pacific Entomologist. 61(1):60-67. (1985).", null),
      name("Whitfield, 1985", "70"),
      part("The Nearctic species of Deuterixys Mason (Hymenoptera: Braconidae)", "The Pan-Pacific Entomologist", "61(1)", 1985, null, null)
    );
    assertTrue(v.contradicted());
  }

  static Reference withPage(Reference r, String page) {
    if (r.getCsl() == null) {
      r.setCsl(new CslData());
    }
    r.getCsl().setPage(page);
    return r;
  }

  @Test
  public void pageOfReference() {
    // COL references often carry the volume and issue in their page
    var bhl = part("A new species of Leongathus from the Tasman Sea collected during the 2003 NORFANZ Expedition (Crustacea: Amphipoda: Phoxocephalidae)", "Memoirs of Museum Victoria", "63", 2006, null, null);
    for (String page : new String[]{"63(2), 207-213", "63(2):207-213", "63: 207-213.", "207-213", "pp. 207-13"}) {
      var v = cmp(
        name("Taylor, 2006", null),
        withPage(cit("Taylor, J. (2006). A new species of Leongathus from the Tasman Sea.", 2006), page),
        name("Taylor, 2006", "207"),
        bhl
      );
      assertFalse(page, v.contradicted());
      assertTrue(page, v.samePage());
    }
    var v = cmp(
      name("Taylor, 2006", null),
      withPage(cit("Taylor, J. (2006). A new species of Leongathus from the Tasman Sea.", 2006), "63(2), 207-213"),
      name("Taylor, 2006", "215"),
      bhl
    );
    assertTrue(v.contradicted());

    // a broken range tells nothing
    v = cmp(
      name("Thuy, 2013", null),
      withPage(cit("Thuy, B. (2013). Temporary expansion to shelf depths rather than an onshore-offshore trend.", 2013), "48-1"),
      name("Thuy, 2013", "70"),
      item("European Journal of Taxonomy", "no.48 (2013)", 2013, null, null)
    );
    assertFalse(v.contradicted());
    assertFalse(v.samePage());
  }

  @Test
  public void differentPageContradicts() {
    // an index volume citing the name, not the original description
    var v = cmp(
      name("Tenison Woods, 1877", "p. 138."),
      cit("Tenison Woods, J. E. (1877). On some new Tasmanian marine shells. (Second series). Papers and Proceedings and Report of the Royal Society of Tasmania, 1876: 131-159.", 1877),
      name("Tenison Woods, 1877", "25"),
      item("The Zoological record", "v.13 (1876)", 1876, null, "https://www.biodiversitylibrary.org/item/209173")
    );
    assertTrue(v.contradicted());
    assertFalse(v.samePage());
    assertFalse(v.stub());
    assertNull(v.doi());

    // off by one is still a different page, the link would point elsewhere than the page given
    v = cmp(
      name("Lessert, 1933", "145"),
      cit("Lessert, R. de (1933). Araignées d'Angola. Résultats de la Mission scientifique suisse en Angola 1928-1929. Revue Suisse de Zoologie 40(1): 85-159.", 1933),
      name("Lessert, 1933", "146"),
      part("Araignées d’Angola", "Revue Suisse De Zoologie", "40", 1933, "10.5962/bhl.part.117656", "https://www.biodiversitylibrary.org/part/117656")
    );
    assertTrue(v.contradicted());
  }

  @Test
  public void yearRangeOfVolume() {
    // North American flora v.9 appeared in parts from 1907 to 1916
    var v = cmp(
      name("(Ellis & Everh.) Theiss. & Syd.", "310"),
      cit("(1916). N. Amer. Fl. (New York) 9(5): 310.", 1916),
      name("(Ellis & Everh.) Theiss. & Syd.", "310"),
      item("North American flora", "v. 9 (1907-16)", 1907, null, "https://www.biodiversitylibrary.org/item/154")
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());
  }

  @Test
  public void yearOfVolume() {
    // BHL gives the first year of the serial for some volumes, but the volume itself names its year
    var v = cmp(
      name("Fieber, 1864", "65"),
      cit("Fieber, F. X. Neuere Entdeckungen in europäischen Hemipteren. Wiener entomologische Monatschrift 8: 65--86, 205--236, 321--336. (1864).", 1864),
      name("Fieber, 1864", "65"),
      item("Wiener entomologische Monatschrift", "8 (1864)", 1857, null, "https://www.biodiversitylibrary.org/item/133105")
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());
  }

  @Test
  public void years() {
    // one year apart happens often with volumes spanning a year end and is tolerated
    var v = cmp(
      name("Hansen, 1976", "76"),
      cit("Hansen, D.C. & Cook, E.F. (1976) The systematics and morphology of the Nearctic species of Diamesa Meigen, 1835 (Diptera: Chironomidae). Mem. Am. Ent. Soc. 30, 1-203.", 1976),
      name("Hansen, 1976", "76"),
      part("The systematics and morphology of the Nearctic species of Diamesa Meigen 1835 (Diptera: Chironomidae)", "Memoirs of the American Entomological Society", "30", 1977, null, null)
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());
    // ... but it is no positive evidence either
    assertFalse(v.sameWork());

    v = cmp(
      name("Guillaumin", null),
      cit("Guillaumin. (1933). Bull. Mus. Natl. Hist. Nat., Sér. 2, 5: 245 (1933), nomen.", 1933),
      name("Guillaumin", null),
      part("New and Rare Malayan Plants Series X", "Journal of the Straits Branch of the Royal Asiatic Society", "78", 1918, null, null)
    );
    assertTrue(v.contradicted());
  }

  @Test
  public void journalTitleIsNoEvidence() {
    // the title of a volume is the journal, which every article in it also mentions
    var v = cmp(
      name("Butler, 1881", null),
      cit("Butler. In: Proceedings of the Zoological Society of London: Vol. 1881 Pg. 623. (1881).", 1881),
      name("Butler, 1881", "1034"),
      item("Proceedings of the Zoological Society of London", "1881", 1881, null, "https://www.biodiversitylibrary.org/item/97245")
    );
    assertNothing(v);
  }

  @Test
  public void sameDoi() {
    var v = cmp(
      name("Taylor, 2006", null),
      cit("Taylor, J. (2006). A new species of Leongathus from the Tasman Sea collected during the 2003 NORFANZ Expedition (Crustacea: Amphipoda: Phoxocephalidae). Memoirs of Museum Victoria, 63(1), 1-6.", 2006, "10.24199/J.MMV.2006.63.16"),
      name("Taylor, 2006", "207"),
      part("A new species of Leongathus from the Tasman Sea collected during the 2003 NORFANZ Expedition (Crustacea: Amphipoda: Phoxocephalidae)", "Memoirs of Museum Victoria", "63", 2006, "10.24199/j.mmv.2006.63.16", "https://www.biodiversitylibrary.org/part/175724")
    );
    assertFalse(v.contradicted());
    assertTrue(v.sameWork());
    // it has one already
    assertNull(v.doi());
  }

  @Test
  public void differentDoiContradicts() {
    var v = cmp(
      name("Bassett-Smith, 1898", null),
      cit("Bassett-Smith, P. W. (1898). Further new parasitic copepods found on fish in the Indo-tropic region. Annals and Magazine of Natural History, 2(8), 77-98.", 1898, "10.1080/00222939808678025"),
      name("Bassett-Smith, 1898", "98"),
      part("XIV.—The species of scorpions of the genus Broteas", "The Annals and Magazine of Natural History", "2", 1898, "10.1080/00222939808678027", null)
    );
    assertTrue(v.contradicted());
  }

  @Test
  public void doiOfSameArticle() {
    var v = cmp(
      name("Evans, 1966", "100"),
      cit("Evans, J.W. (1966a) The leafhoppers and froghoppers of Australia and New Zealand (Homoptera: Cicadelloidea and Cercopoidea). Memoirs of the Australian Museum, 12, 1–347.", null),
      name("Evans, 1966", "100"),
      part("The leafhoppers and froghoppers of Australia and New Zealand (Homoptera: Cicadelloidea and Cercopoidea)", "The Australian Museum Memoir", "12", 1966, "10.3853/j.0067-1967.12.1966.425", "https://doi.org/10.3853/j.0067-1967.12.1966.425")
    );
    assertFalse(v.contradicted());
    assertTrue(v.samePage());
    assertTrue(v.sameWork());
    assertEquals(DOI.parse("10.3853/j.0067-1967.12.1966.425").get(), v.doi());

    // no year at all: the title alone is not enough
    v = cmp(
      name("Evans", "100"),
      cit("Evans, J.W. The leafhoppers and froghoppers of Australia and New Zealand (Homoptera: Cicadelloidea and Cercopoidea). Memoirs of the Australian Museum.", null),
      name("Evans", "100"),
      part("The leafhoppers and froghoppers of Australia and New Zealand (Homoptera: Cicadelloidea and Cercopoidea)", "The Australian Museum Memoir", "12", 1966, "10.3853/j.0067-1967.12.1966.425", null)
    );
    assertFalse(v.contradicted());
    assertFalse(v.sameWork());
    assertNull(v.doi());
  }

  @Test
  public void titleTooShort() {
    var v = cmp(
      name("Lessert, 1933", null),
      cit("Lessert, R. de (1933). Araignées d'Angola. Revue Suisse de Zoologie 40.", 1933),
      name("Lessert, 1933", "146"),
      part("Araignées d’Angola", "Revue Suisse De Zoologie", "40", 1933, "10.5962/bhl.part.117656", null)
    );
    assertFalse(v.contradicted());
    assertFalse(v.sameWork());
  }

  @Test
  public void stub() {
    // Acacia citrinoviridis, the second example of #1606
    Reference stub = cit("Tindale, & Maslin. (1976).", 1976);
    stub.getCsl().setAuthor(new CslName[]{new CslName("Tindale"), new CslName("Maslin")});
    var v = cmp(
      name("Tindale & Maslin", null),
      stub,
      name("Tindale & Maslin", "86"),
      part("Two new species of Acacia from Western Australia", "Nuytsia: journal of the Western Australian Herbarium", "2", 1976, "10.58828/nuy00029", "https://www.biodiversitylibrary.org/page/53137461")
    );
    assertFalse(v.contradicted());
    assertTrue(v.stub());
    // never write into a stub, it is shared by every name of these authors and year
    assertNull(v.doi());

    v = cmp(
      name("A.Cunn. ex Benth.", null),
      cit("A.Cunn. ex Benth. (1842).", 1842),
      name("A.Cunn. ex Benth.", "489"),
      item("The London journal of botany", "v.1 (1842)", 1842, null, "https://www.biodiversitylibrary.org/item/40367")
    );
    assertTrue(v.stub());

    v = cmp(
      name("Gardner ex Court", null),
      cit("Gardner ex Court, C. A. (1978).", 1978),
      name("Gardner ex Court", "3"),
      item("Muelleria", "v.4 (1978)", 1978, null, null)
    );
    assertTrue(v.stub());
  }

  @Test
  public void stubRequiresSameYear() {
    var v = cmp(
      name("Benth.", null),
      cit("Benth. (1842).", 1842),
      name("Benth.", "489"),
      item("The London journal of botany", "v.1", 1850, null, null)
    );
    assertTrue(v.contradicted());
    assertFalse(v.stub());

    // no year at all is not enough either
    v = cmp(
      name("Benth.", null),
      cit("Benth.", null),
      name("Benth.", "489"),
      item("The London journal of botany", "v.1", 1842, null, null)
    );
    assertFalse(v.stub());
  }

  @Test
  public void stubUsesCombinationYear() {
    var v = cmp(
      name("Benth.", "1842", null),
      cit("Benth.", null),
      name("Benth.", "489"),
      item("The London journal of botany", "v.1 (1842)", 1842, null, null)
    );
    assertTrue(v.stub());
  }

  @Test
  public void noStub() {
    // the journal and page carry information the name alone does not
    var v = cmp(
      name("J.J.Sm.", null),
      cit("Orch. Java: 279 (1905)", 1905),
      name("J.J.Sm.", "279"),
      item("Flore de Buitenzorg", "pt.6 (1905)", 1905, null, null)
    );
    assertFalse(v.stub());
    assertTrue(v.samePage());

    // another author is not the authorship of the name
    v = cmp(
      name("Benth.", null),
      cit("Miller. (1842).", 1842),
      name("Benth.", "489"),
      item("The London journal of botany", "v.1 (1842)", 1842, null, null)
    );
    assertFalse(v.stub());

    // a stub replacing a stub gains nothing
    v = cmp(
      name("Benth.", null),
      cit("Benth. (1842).", 1842),
      name("Benth.", "489"),
      cit("Benth. (1842)", 1842)
    );
    assertFalse(v.stub());
  }
}
