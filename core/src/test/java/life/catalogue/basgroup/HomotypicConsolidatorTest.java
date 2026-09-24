package life.catalogue.basgroup;

import life.catalogue.TestUtils;
import life.catalogue.api.model.ConsolidationName;
import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.DatasetInfoCache;

import life.catalogue.matching.similarity.ModifiedDamerauLevenshtein;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HomotypicConsolidatorTest {
  private DatasetInfoCache infoCache;

  // findPrimaryUsage replaces the static cache by a mock, which a later test class in the same fork would read otherwise
  @Before
  public void keepInfoCache() {
    infoCache = DatasetInfoCache.CACHE;
  }

  @After
  public void restoreInfoCache() {
    DatasetInfoCache.CACHE = infoCache;
  }

  @Test
  public void isSameName() throws Exception {
    compare("Abies", "Abies", true);
    compare("Abies alba", "Abies alba", true);
    compare("Abies alba", "Abies alpa", true);
    compare("Abies alba", "Apia alba", false);
    compare("Mesolecanium nigrofasciatum", "Mesolecanium nigrofaciatum", true);
    compare("Mesolecanium nigrofasciatum", "Mesolecanium nicrofaciatum", false);
  }

  @Test
  public void isOrthographicVariant() throws Exception {
    variant("Trachelosiphon colombianum Schltr.", "Trachelosiphon columbianum Schltr.", Rank.SPECIES, true);
    variant("Bdellodes iranensis Ueckermann", "Bdellodes iraniensis Ueckermann", Rank.SPECIES, true);
    // gender endings
    variant("Aus albus L.", "Aus alba L.", Rank.SPECIES, true);
    variant("Aus bus var. cus Miller", "Aus bus var. kus Miller", Rank.VARIETY, true);
    // the same spelling is a duplicate, not a variant
    variant("Aus albus L.", "Aus albus Linnaeus", Rank.SPECIES, false);
    // another genus, even if only misspelled
    variant("Bisciris meridionalis Thor", "Biscirus meridionalis Thor", Rank.SPECIES, false);
    variant("Octomeria colombiana Schltr.", "Aa colombiana Schltr.", Rank.SPECIES, false);
    // another species
    variant("Aus bus var. cus Miller", "Aus dus var. kus Miller", Rank.VARIETY, false);
    // another subgenus
    variant("Cyclops (Acanthocyclops) stammeri Kiefer", "Cyclops (Megacyclops) stammerii Kiefer", Rank.SPECIES, false);
    // another rank
    assertFalse(HomotypicConsolidator.isOrthographicVariant(parse("Negundo violaceum G.Kirchn.", Rank.SPECIES),
      parse("Negundo aceroides var. violacea G.Kirchn.", Rank.VARIETY)));
  }

  void variant(String n1, String n2, Rank rank, boolean expected) throws Exception {
    assertEquals(n1 + " vs " + n2, expected, HomotypicConsolidator.isOrthographicVariant(parse(n1, rank), parse(n2, rank)));
    assertEquals(n2 + " vs " + n1, expected, HomotypicConsolidator.isOrthographicVariant(parse(n2, rank), parse(n1, rank)));
  }

  static Name parse(String name, Rank rank) throws Exception {
    return NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName();
  }

  void compare(String n1, String n2, boolean same) {
    var cn1 = new ConsolidationName();
    cn1.setName(n1);
    var cn2 = new ConsolidationName();
    cn2.setName(n2);
    assertEquals(same, HomotypicConsolidator.isSameName(cn1, cn2, new ModifiedDamerauLevenshtein()));
  }

  @Test
  public void findPrimaryUsage() throws Exception {
    var session = mock(SqlSession.class);
    var factory = mock(SqlSessionFactory.class);

    var mockedCache = TestUtils.mockedInfoCache();
    var info = new DatasetInfoCache.DatasetInfo(3, DatasetOrigin.PROJECT,3, null, false);
    when(mockedCache.info(anyInt())).thenReturn(info);

    var hc = HomotypicConsolidator.forTaxa(factory, 3, List.of(), u -> 1);

    var bg = new HomotypicGroup<LinneanNameUsage>(null, "sapiens", Authorship.authors("Linnaeus"), NomCode.ZOOLOGICAL);
    bg.setBasionym(lnu("1", Rank.SUBSPECIES, "Nasua olivacea quitensis", "Lönnberg, 1913"));
    bg.addRecombination(lnu("2", Rank.SUBSPECIES, "Nasuella olivacea quitensis", "(Lönnberg, 1913)", TaxonomicStatus.SYNONYM, "1"));
    var primary = hc.findPrimaryUsage(bg, session);
    assertEquals("1", primary.getId());
  }

  public static LinneanNameUsage lnu(String id, Rank rank, String name, String authorship) {
    return lnu(id, rank, name, authorship, TaxonomicStatus.ACCEPTED, null);
  }
  public static LinneanNameUsage lnu(String id, Rank rank, String name, String authorship, TaxonomicStatus status, String parentID) {
    LinneanNameUsage lnu = new LinneanNameUsage();
    lnu.setId(id);
    lnu.setParentId(parentID);
    lnu.setRank(rank);
    lnu.setAuthorship(authorship);
    lnu.setScientificName(name);
    lnu.setStatus(status);
    return lnu;
  }
}