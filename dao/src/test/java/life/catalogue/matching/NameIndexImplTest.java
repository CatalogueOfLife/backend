package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Origin;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NameIndexImpl;
import life.catalogue.matching.nidx.NamesIndexConfig;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test of the slim, single-tier, canonical-only names index against a mocked postgres.
 * A match now resolves to a bare nidx int; authorship and rank are ignored, so every spelling of a
 * name collapses onto the one canonical entry for its normalized bucket.
 */
@RunWith(MockitoJUnitRunner.class)
public class NameIndexImplTest {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;

  NameIndex ni;
  AtomicInteger keyGen = new AtomicInteger(1);

  @Before
  public void setup() throws Exception {
    SqlSessionFactory factory = mock(SqlSessionFactory.class);
    SqlSession session = mock(SqlSession.class);
    NamesIndexMapper mapper = mock(NamesIndexMapper.class);

    when(factory.openSession()).thenReturn(session);
    when(session.getMapper(any())).thenReturn(mapper);
    // assign-on-miss insert: mimic postgres by handing out sequential ids
    doAnswer(invocation -> {
      IndexName param = invocation.getArgument(0, IndexName.class);
      param.setKey(keyGen.getAndIncrement());
      return null;
    }).when(mapper).createOnConflict(any());

    ni = NameIndexFactory.build(NamesIndexConfig.memory(512), factory, aNormalizer).started();
    assertEquals(0, ni.size());
  }

  @After
  public void stop() throws Exception {
    if (ni != null) {
      ni.close();
    }
  }

  void addTestNames() throws Exception {
    // single-tier: authorship & rank are collapsed onto the canonical, so the canonical key each name
    // ends up on is noted below. Names that share a canonical (or stemmed canonical) reuse its key.
    List.of(
      // canonical 1: Animalia
      "Animalia|KINGDOM",
      // canonical 2: Oenanthe
      "Oenanthe Vieillot, 1816|GENUS",
      "Oenanthe Pallas, 1771|GENUS",
      "Oenanthe L.|GENUS",
      // canonical 3: Oenanthe aquatica
      "Oenanthe aquatica|SPECIES",
      "Oenanthe aquatica Poir.|SPECIES",
      "Oenanthe aquatica Senser, 1957|SPECIES",
      // canonical 4: Natting tosee
      "Natting tosee|SPECIES",
      // canonical 5: Abies alba
      "Abies alba|SPECIES",
      "Abies alba Mumpf.|SPECIES",
      "Abies alba 1778|SPECIES",
      // canonical 6: Picea alba
      "Picea alba 1778|SPECIES",
      // canonical 7: Picea
      "Picea|GENUS",
      // canonical 8: Carex cayouettei
      "Carex cayouettei|SPECIES",
      // canonical 9: Carex comosa × Carex lupulina
      "Carex comosa × Carex lupulina|SPECIES",
      // canonical 10: Natting borealis
      "Natting borealis|SPECIES",
      // canonical 11: Natting montana
      "Natting montana|SPECIES",
      // canonical 12: Natting silvestris
      "Natting silvestris|SPECIES",
      // canonical 13: Natting palustris
      "Natting palustris|SPECIES",
      // canonical 14: Rodentia
      "Rodentia|GENUS",
      "Rodentia Bowdich, 1821|ORDER",
      // canonical 15: Aeropyrum coil-shaped virus
      "Aeropyrum coil-shaped virus|UNRANKED"
    ).forEach(s -> {
      String[] parts = s.split("\\|");
      try {
        ni.match(name(parts[0], Rank.valueOf(parts[1])), true, false);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });
    assertEquals(15, ni.size());
  }

  /**
   * Try to add the same name again and multiple names with the same key
   */
  @Test
  public void add() throws Exception {
    ni.match(create("Abies", "krösus-4-paratos"), true, false);
    ni.match(create("Abies", "alba"), true, false);
    ni.match(create("Abies", "alba", "1873"), true, false);
    // single-tier: only the 2 distinct canonical names get an entry; the differently-dated
    // "Abies alba" collapses into the already existing canonical entry
    assertEquals(2, ni.size());

    // same canonical again, just with an author added - no new entry
    ni.match(create("Abies", "alba", "1873", "Miller"), true, false);
    assertEquals(2, ni.size());

    // a genuinely new canonical name
    ni.match(create("Abies", "perma", "1901", "Jones"), true, false);
    assertEquals(3, ni.size());
  }

  /**
   * The names index is single-tier: matching a ranked, authored name must only ever create the one
   * canonical entry keyed by its normalized canonical bucket.
   */
  @Test
  public void addIsSingleTier() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setRank(Rank.SPECIES);
    n.setCombinationAuthorship(Authorship.authors("Mill."));
    n.rebuildScientificName();
    n.rebuildAuthorship();

    var m = ni.match(n, true, false);
    assertTrue(m.isMatched());
    // the store key is the stemmed, normalized canonical: "alba" stems to "alb" (SciNameNormalizer)
    assertEquals((int) m.getNidx(), ni.store().get("abies alb"));
    assertTrue(ni.store().contains("abies alb"));
    assertEquals(1, ni.size());
  }

  @Test
  public void basionymPlaceholders() throws Exception {
    Name n = new Name();
    n.setRank(Rank.GENUS);
    n.setUninomial("?");
    n.setAuthorship("Nardo");
    n.setCombinationAuthorship(Authorship.authors("Nardo"));
    n.setCode(NomCode.ZOOLOGICAL);
    n.setType(NameType.PLACEHOLDER);
    assertNoInsert(n);

    n = new Name();
    n.setUninomial("'");
    n.setCode(NomCode.ZOOLOGICAL);
    n.setOrigin(Origin.IMPLICIT_NAME);
    n.setType(NameType.SCIENTIFIC);
    assertNoInsert(n);

    n = new Name();
    n.setRank(Rank.UNRANKED);
    n.setUninomial("..");
    n.setOrigin(Origin.VERBATIM_ACCEPTED);
    n.setType(NameType.OTHER);
    assertNoInsert(n);

    n = new Name();
    n.setRank(Rank.SUBGENUS);
    n.setInfragenericEpithet("?");
    n.setAuthorship("Nardo");
    n.setCombinationAuthorship(Authorship.authors("Nardo"));
    n.setType(NameType.SCIENTIFIC);
    var quest = assertInsert(n);

    n = new Name();
    n.setUninomial("'");
    n.setRank(Rank.FAMILY);
    n.setCode(NomCode.ZOOLOGICAL);
    n.setType(NameType.SCIENTIFIC);
    assertMatch(quest.getNidx(), n); // matches the weird canonical "?"

    // good infragenerics: single-tier collapses every "Tragulla" spelling onto one canonical uninomial
    n = new Name();
    n.setInfragenericEpithet("Tragulla");
    n.setRank(Rank.SUBGENUS);
    n.setType(NameType.SCIENTIFIC);
    var tragulla = assertInsert(n);
    final int tragullaKey = tragulla.getNidx();

    // same canonical with an authorship - matches, no new entry
    n = new Name();
    n.setInfragenericEpithet("Tragulla");
    n.setAuthorship("Nardo");
    n.setCombinationAuthorship(Authorship.authors("Nardo"));
    n.setRank(Rank.SUBGENUS);
    n.setType(NameType.SCIENTIFIC);
    assertMatch(tragullaKey, n);

    // same canonical uninomial, now with a genus placement & section rank - still matches
    n = new Name();
    n.setGenus("Triceps");
    n.setInfragenericEpithet("Tragulla");
    n.setAuthorship("Nardo");
    n.setCombinationAuthorship(Authorship.authors("Nardo"));
    n.setRank(Rank.SECTION_BOTANY);
    n.setType(NameType.SCIENTIFIC);
    assertMatch(tragullaKey, n);
  }

  private void assertNoInsert(Name n) {
    final int origSize = ni.size();
    n.rebuildScientificName();
    var m = ni.match(n, true, false);

    assertFalse(m.isMatched());
    assertNull(m.getNidx());
    assertEquals(origSize, ni.size());
  }

  private void assertMatch(int key, Name n) {
    final int origSize = ni.size();
    n.rebuildScientificName();
    var m = ni.match(n, true, false);

    assertTrue(m.isMatched());
    assertEquals(key, (int) m.getNidx());
    assertEquals(origSize, ni.size());
  }

  private NameMatch assertInsert(Name n) {
    final int origSize = ni.size();
    n.rebuildScientificName();
    var m = ni.match(n, true, false);

    assertTrue(m.isMatched());
    // single-tier: inserting a genuinely new canonical name adds exactly one record
    assertEquals(origSize + 1, ni.size());
    return m;
  }

  /**
   * match the same name over and over again to make sure we never insert duplicates
   */
  @Test
  public void avoidDuplicates() throws Exception {
    Name n = new Name();
    n.setScientificName("Abies alba");
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setAuthorship("Mill.");
    n.setCombinationAuthorship(Authorship.authors("Mill."));
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);

    NameMatch m = ni.match(n, true, false);
    assertTrue(m.isMatched());
    final Integer idx = m.getNidx();
    assertEquals(1, ni.size());

    m = ni.match(n, true, false);
    assertEquals(idx, m.getNidx());
    assertEquals(1, ni.size());

    m = ni.match(n, true, false);
    assertEquals(idx, m.getNidx());
    assertEquals(1, ni.size());

    // a different authorship no longer creates a separate entry
    n.setAuthorship("Miller");
    n.setCombinationAuthorship(Authorship.authors("Miller"));
    m = ni.match(n, true, false);
    assertEquals(idx, m.getNidx());
    assertEquals(1, ni.size());

    n.setAuthorship("Tesla");
    n.setCombinationAuthorship(Authorship.authors("Tesla"));
    m = ni.match(n, true, false);
    assertEquals(idx, m.getNidx());
    assertEquals(1, ni.size());
  }

  @Test
  public void infraspecifics() throws Exception {
    final Name n1 = new Name();
    n1.setScientificName("Abies alba alba");
    n1.setGenus("Abies");
    n1.setSpecificEpithet("alba");
    n1.setInfraspecificEpithet("alba");
    n1.setAuthorship("Mill.");
    n1.setCombinationAuthorship(Authorship.authors("Mill."));
    n1.setRank(Rank.SUBSPECIES);
    n1.setType(NameType.SCIENTIFIC);

    NameMatch m = ni.match(n1, true, false);
    assertTrue(m.isMatched());
    final int key = m.getNidx();
    assertEquals(1, ni.size());

    // single-tier & canonical-only: every rank / authorship / notho / rank-marker spelling of the
    // same trinomial collapses onto the very same single canonical entry.

    // just a different rank
    m = matchNameCopy(n1, n -> n.setRank(Rank.VARIETY));
    assertEquals(key, (int) m.getNidx());

    // rank marker in the scientificName is irrelevant - canonical is built from the epithets
    m = matchNameCopy(n1, n -> {
      n.setRank(Rank.VARIETY);
      n.setScientificName("Abies alba var. alba");
    });
    assertEquals(key, (int) m.getNidx());

    // notho / hybrid marker is likewise stripped from the canonical
    m = matchNameCopy(n1, n -> {
      n.setRank(Rank.VARIETY);
      n.setScientificName("Abies alba × alba");
      n.setNotho(NamePart.INFRASPECIFIC);
    });
    assertEquals(key, (int) m.getNidx());

    // a yet different rank
    m = matchNameCopy(n1, n -> n.setRank(Rank.FORM));
    assertEquals(key, (int) m.getNidx());

    // a different authorship no longer matters
    m = matchNameCopy(n1, n -> {
      n.setRank(Rank.FORM);
      n.setCombinationAuthorship(Authorship.authors("Miller"));
      n.setScientificName("Abies alba f. alba");
      n.setAuthorship("Miller");
    });
    assertEquals(key, (int) m.getNidx());

    // no authorship
    m = matchNameCopy(n1, n -> {
      n.setRank(Rank.FORM);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
      n.setScientificName("Abies alba f. alba");
    });
    assertEquals(key, (int) m.getNidx());

    // no authorship, no rank -> the plain canonical query
    m = matchNameCopy(n1, n -> {
      n.setRank(Rank.UNRANKED);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
      n.setScientificName("Abies alba f. alba");
    });
    assertEquals(key, (int) m.getNidx());

    // nothing new was ever inserted
    assertEquals(1, ni.size());
  }

  @Test
  public void higher() throws Exception {
    Name n1 = new Name();
    n1.setScientificName("Puma");
    n1.setUninomial("Puma");
    n1.setAuthorship("L.");
    n1.setCombinationAuthorship(Authorship.authors("L."));
    n1.setRank(Rank.GENUS);
    n1.setType(NameType.SCIENTIFIC);

    NameMatch m = ni.match(n1, true, false);
    assertTrue(m.isMatched());
    final int key = m.getNidx();
    assertEquals(1, ni.size());

    // given as a subgenus - same canonical uninomial "Puma"
    m = matchNameCopy(n1, n -> {
      n.setInfragenericEpithet(n.getUninomial());
      n.setUninomial(null);
      n.setRank(Rank.SUBGENUS);
    });
    assertEquals(key, (int) m.getNidx());

    // a different authorship no longer matters
    m = matchNameCopy(n1, n -> {
      n.setAuthorship("Linné");
      n.setCombinationAuthorship(Authorship.authors("Linné"));
    });
    assertEquals(key, (int) m.getNidx());

    // ranked, but no authorship
    m = matchNameCopy(n1, n -> {
      n.setRank(Rank.GENUS);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
    });
    assertEquals(key, (int) m.getNidx());

    // the plain canonical query
    m = matchNameCopy(n1, n -> {
      n.setRank(Rank.UNRANKED);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
    });
    assertEquals(key, (int) m.getNidx());

    assertEquals(1, ni.size());
  }

  private NameMatch matchNameCopy(Name original, Consumer<Name> modifier) {
    Name n = new Name(original);
    modifier.accept(n);
    NameMatch nm = ni.match(n, true, false);
    assertTrue(nm.isMatched());
    return nm;
  }

  @Test
  public void unparsed() throws Exception {
    assertEquals(0, ni.size());

    var names = Stream.of(
      // FLOW placeholder - not indexed
      Name.newBuilder()
          .scientificName("Aphaena dives var. [unnamed]")
          .authorship("Walker, 1851")
          .rank(Rank.SUBSPECIES)
          .type(NameType.PLACEHOLDER)
          .code(NomCode.ZOOLOGICAL),
      // ICTV virus (now OTHER + NomCode.VIRUS, indexed)
      Name.newBuilder()
          .scientificName("Abutilon mosaic Bolivia virus")
          .rank(Rank.SPECIES)
          .type(NameType.OTHER)
          .code(NomCode.VIRUS),
      Name.newBuilder()
          .scientificName("Abutilon mosaic Brazil virus")
          .rank(Rank.SPECIES)
          .type(NameType.OTHER)
          .code(NomCode.VIRUS),
      // GTDB OTU (OTHER, indexed)
      Name.newBuilder()
          .scientificName("AABM5-125-24")
          .rank(Rank.PHYLUM)
          .type(NameType.OTHER),
      Name.newBuilder()
          .scientificName("Aureabacteria_A")
          .rank(Rank.PHYLUM)
          .type(NameType.OTHER)
          .code(NomCode.BACTERIAL),
      // GTDB informal
      Name.newBuilder()
          .scientificName("Aalborg-Aaw sp.")
          .genus("Aalborg-Aaw")
          .rank(Rank.SPECIES)
          .type(NameType.INFORMAL)
          .code(NomCode.BACTERIAL),
      Name.newBuilder()
          .scientificName("Actinomarina sp.")
          .genus("Actinomarina")
          .rank(Rank.SPECIES)
          .type(NameType.INFORMAL)
          .code(NomCode.BACTERIAL),
      // GTDB no name (OTHER, indexed)
      Name.newBuilder()
          .scientificName("B3-LCP")
          .rank(Rank.CLASS)
          .type(NameType.OTHER),
      // VASCAN hybrid
      Name.newBuilder()
          .scientificName("Agropyron cristatum × Agropyron fragile")
          .rank(Rank.SPECIES)
          .type(NameType.FORMULA)
          .code(NomCode.BOTANICAL)
    ).map(Name.Builder::build).collect(Collectors.toList());

    for (int repeat = 1; repeat < 3; repeat++) {
      for (Name n : names) {
        var m = ni.match(n, true, false);
        if (NameIndexImpl.INDEX_NAME_TYPES.contains(n.getType())) {
          assertTrue(m.isMatched());
          assertNotNull(m.getNidx());
        } else {
          assertFalse(m.isMatched());
        }
      }
    }

    // every type but PLACEHOLDER is indexed: 5 OTHER + 2 INFORMAL + 1 FORMULA names, 1 canonical idxn each
    assertEquals(8, ni.size());
  }

  private static Name create(String genus, String species) {
    return create(genus, species, null);
  }

  private static Name create(String genus, String species, String year, String... authors) {
    Name n = new Name();
    if (authors != null || year != null) {
      n.setCombinationAuthorship(Authorship.yearAuthors(year, authors));
    }
    n.setGenus(genus);
    n.setSpecificEpithet(species);
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);
    n.rebuildScientificName();
    n.rebuildAuthorship();

    return n;
  }

  @Test
  public void insertNewNames() throws Exception {
    assertInsert("Larus fundatus", Rank.SPECIES);
    assertInsert("Puma concolor", Rank.SPECIES);
  }

  @Test
  public void unrankedNames() throws Exception {
    // single-tier: "Biota" (with or without authorship, at any rank) is a single canonical entry.
    var m = assertInsert("Biota", Rank.DOMAIN);
    int key = m.getNidx();
    assertEquals(1, ni.size());
    // "Biota End." has canonical "Biota" (End. is just authorship), so it matches the same entry
    m = assertMatch(key, "Biota End.", Rank.GENUS);
    m = assertMatch(key, "Biota", Rank.UNRANKED);
    assertEquals(1, ni.size());

    stop();
    setup();
    // order independence: starting from the unranked spelling yields the same single canonical entry
    m = assertInsert("Biota", Rank.UNRANKED);
    key = m.getNidx();
    assertEquals(1, ni.size());
    m = assertMatch(key, "Biota End.", Rank.GENUS);
    m = assertMatch(key, "Biota", Rank.DOMAIN);
    assertEquals(1, ni.size());
  }

  @Test
  public void testLookup() throws Exception {
    addTestNames();

    // single-tier: authorship & rank are ignored - every spelling of a name resolves to its single
    // canonical entry (see the canonical keys noted in addTestNames)
    assertMatch(1, "Animalia", Rank.KINGDOM);

    assertMatch(14, "Rodentia", Rank.GENUS);
    assertMatch(14, "Rodentia", Rank.ORDER);
    assertNoMatch("Rodenti", Rank.ORDER);

    // all authorship/year spellings of Rodentia collapse onto its canonical
    assertMatch(14, "Rodentia Bowdich, 1821", Rank.ORDER);
    assertMatch(14, "Rodentia Bowdich, 1?21", Rank.ORDER);
    assertMatch(14, "Rodentia Bowdich", Rank.ORDER);
    assertMatch(14, "Rodentia 1821", Rank.ORDER);
    assertMatch(14, "Rodentia Bow.", Rank.ORDER);
    assertMatch(14, "Rodentia Bow, 1821", Rank.ORDER);
    assertMatch(14, "Rodentia B 1821", Rank.ORDER);
    assertMatch(14, "Rodentia", Rank.FAMILY);
    assertMatch(14, "Rodentia Mill., 1823", Rank.SUBORDER);

    assertMatch(2, "Oenanthe", Rank.GENUS);
    assertMatch(2, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(2, "Oenanthe V", Rank.GENUS);
    assertMatch(2, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(2, "Oenanthe P", Rank.GENUS);
    assertMatch(2, "Oenanthe Pal", Rank.GENUS);
    assertMatch(2, "Œnanthe 1771", Rank.GENUS);
    assertMatch(2, "Œnanthe", Rank.GENUS);
    assertMatch(2, "Oenanthe Camelot", Rank.GENUS);

    assertMatch(3, "Oenanthe aquatica", Rank.SPECIES);
    assertMatch(3, "Oenanthe aquatica Poir", Rank.SPECIES);
    assertMatch(3, "Œnanthe aquatica", Rank.SPECIES);

    // authorship no longer differentiates - all resolve to the single canonical Abies alba
    assertMatch(5, "Abies alba", Rank.SPECIES);
    assertMatch(5, "Abies alba Döring, 1778", Rank.SPECIES);
    assertMatch(5, "Abies alba Mumpf.", Rank.SPECIES);
    assertMatch(5, "Abies alba Mill.", Rank.SPECIES);
    assertMatch(5, "Abies alba 1789", Rank.SPECIES);

    // try unparsable names
    assertMatch(8, "Carex cayouettei", Rank.SPECIES);
    assertMatch(9, "Carex comosa × Carex lupulina", Rank.SPECIES);
    assertMatch(15, "Aeropyrum coil-shaped virus", Rank.UNRANKED);
    assertMatch(15, "Aeropyrum coil-shaped virus", Rank.SPECIES); // given in index as UNRANKED
  }

  /**
   * https://github.com/Sp2000/colplus-backend/issues/451
   */
  @Test
  public void testSubgenusLookup() throws Exception {
    // single-tier: each name reduces to one canonical entry. Shared canonicals reuse the key.
    List.of(
      "Animalia|KINGDOM",                                        // canonical 1
      "Zyras|GENUS",                                             // canonical 2
      "Zyras|SUBGENUS",                                          // canonical 2 (same canonical uninomial)
      "Drusilla|GENUS",                                          // canonical 3
      "Drusilla zyrasoides M.Dvořák, 1988|SPECIES",             // canonical 4
      "Myrmedonia (Zyras) alternans Cameron, 1925|SPECIES",     // canonical 5
      "Myrmedonia (Zyras) bangae Cameron, 1926|SPECIES",        // canonical 6
      "Myrmedonia (Zyras) hirsutiventris Champion, 1927|SPECIES", // canonical 7
      "Zyras (Zyras) alternans (Cameron, 1925)|SPECIES",        // canonical 8
      "Zyras bangae (Cameron, 1926)|SPECIES"                    // canonical 9
    ).forEach(s -> {
      String[] parts = s.split("\\|");
      try {
        ni.match(name(parts[0], Rank.valueOf(parts[1])), true, false);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });

    assertEquals(9, ni.size());

    assertMatch(4, "Drusilla zyrasoides", Rank.SPECIES);
    assertMatch(4, "Drusilla zyrasoides", Rank.SPECIES);
    assertMatch(5, "Myrmedonia (Zyras) alternans", Rank.SPECIES);
    assertMatch(5, "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertMatch(5, "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertMatch(5, "Myrmedonia (Larus) alternans Cameron, 1925", Rank.SPECIES);
    // single-tier: a new authorship no longer creates a new entry - it matches the same canonical
    assertMatch(5, "Myrmedonia alternans Krill, 1925", Rank.SPECIES);

    assertEquals(9, ni.size());
  }

  @Test
  public void stemming() throws Exception {
    for (Name n : NameIndexImplIT.prepareTestNames()) {
      var m = ni.match(n, true, false);
      System.out.println(m);
    }
    // single-tier: the test names are "Abies alba"/"Abies albus" with & without authorship. Stemming
    // folds the gender variants (alba/albus) into the same canonical bucket, so all of them collapse
    // onto a single canonical entry.
    assertEquals(1, ni.size());
  }

  @Test
  public void testMissingAuthorBrackets() throws Exception {
    // single-tier: authorship (incl. bracket differences and different authors) is ignored -
    // every "Caretta caretta ..." resolves to the same single canonical entry
    var m = ni.match(name("Caretta caretta Linnaeus", Rank.SPECIES), true, false);
    assertTrue(m.isMatched());
    final int key = m.getNidx();
    assertEquals(1, ni.size());

    m = ni.match(name("Caretta caretta (Linnaeus)", Rank.SPECIES), true, false);
    assertEquals(key, (int) m.getNidx());
    assertEquals(1, ni.size());

    m = ni.match(name("Caretta caretta (Peter)", Rank.SPECIES), true, false);
    assertEquals(key, (int) m.getNidx());
    assertEquals(1, ni.size());
  }

  @Test
  public void testZooAuthors() throws Exception {
    assertEquals(0, ni.size());

    var m = ni.match(name("Chaetocnema belli Jacoby, 1904", Rank.SPECIES), true, false);
    assertTrue(m.isMatched());
    assertEquals(1, ni.size());

    // single-tier: "belli" and "bella" share the same stemmed canonical bucket ("bell") and
    // authorship is ignored, so this collapses onto the very same single canonical entry.
    var m2 = ni.match(name("Chaetocnema bella (Baly, 1876)", Rank.SPECIES), true, false);
    assertTrue(m2.isMatched());
    assertEquals(m.getNidx(), m2.getNidx());
    assertEquals(1, ni.size());
  }

  static Name name(String name, Rank rank) throws InterruptedException {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName());
    n.setRank(rank);
    return n;
  }

  static Name name(String sciname, String authorship, Rank rank) throws InterruptedException {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(sciname, authorship, rank, null, VerbatimRecord.VOID).get().getName());
    n.setRank(rank);
    return n;
  }

  /**
   * The names index is single-tier & canonical-only: a differently-authored, differently-ranked
   * spelling of the same canonical name must resolve to the very same (single) index entry.
   */
  @Test
  public void authorshipIgnoredOnMatch() throws Exception {
    ni.match(name("Abies alba", "Mill.", Rank.SPECIES), true, false);
    var m = ni.match(name("Abies alba", "L.", Rank.SUBSPECIES), false, false);
    assertTrue(m.isMatched());
    assertEquals(1, ni.size());
  }

  /**
   * A unicode spelling variant of an already indexed canonical name must still match it.
   */
  @Test
  public void unicodeVariantMatches() throws Exception {
    ni.match(name("Muller", null, Rank.GENUS), true, false);
    var m = ni.match(name("Müller", null, Rank.GENUS), false, false);
    assertTrue(m.isMatched());
  }

  static IndexName iname(String name, Rank rank) throws InterruptedException {
    return new IndexName(name(name, rank));
  }

  private NameMatch assertNoMatch(String name, Rank rank) throws InterruptedException {
    NameMatch m = match(name, rank);
    assertFalse(m.isMatched());
    assertNull(m.getNidx());
    return m;
  }

  private NameMatch assertMatch(int key, String name, Rank rank) throws InterruptedException {
    NameMatch m = match(name, rank);
    if (!m.isMatched() || key != m.getNidx()) {
      System.err.println(m);
    }
    assertTrue("Expected single match but got none for " + name, m.isMatched());
    assertEquals("Expected " + key + " for " + name, key, (int) m.getNidx());
    return m;
  }

  private NameMatch assertInsert(String name, Rank rank) throws InterruptedException {
    NameMatch m = ni.match(name(name, rank), false, false);
    assertFalse(m.isMatched());
    int cnt = ni.size();
    m = ni.match(name(name, rank), true, false);
    // single-tier: inserting a genuinely new canonical name adds exactly one record
    assertEquals(cnt + 1, ni.size());
    assertTrue(m.isMatched());
    return m;
  }

  private NameMatch match(String name, Rank rank) throws InterruptedException {
    return ni.match(name(name, rank), false, false);
  }

}
