package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Origin;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NameIndexImpl;
import life.catalogue.matching.nidx.NamesIndexConfig;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    //when(mapper.processAll()).thenReturn(new EmptyListCursor<>());
    doAnswer(new Answer<IndexName>() {
      public IndexName answer(InvocationOnMock invocation) {
        IndexName param = invocation.getArgument(0, IndexName.class);
        param.setKey(keyGen.getAndIncrement());
        return param;
      }}
    ).when(mapper).create(any());

    ni = NameIndexFactory.build(NamesIndexConfig.memory(512), factory, aNormalizer).started();
    assertEquals(0, ni.size());
  }

  public static class EmptyListCursor<T> extends ArrayList<T> implements Cursor<T> {

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public boolean isConsumed() {
      return false;
    }

    @Override
    public int getCurrentIndex() {
      return 0;
    }
  }

  @After
  public void stop() throws Exception {
    if (ni != null) {
      dumpIndex();
      ni.close();
    }
  }

  void addTestNames() throws Exception {
    // single-tier: authorship & rank are collapsed onto the canonical, so the canonical key each name
    // ends up on is noted below. Names that share a canonical (or stemmed canonical) reuse its key.
    List.of(
      // canonical 1: Animalia
      iname("Animalia", Rank.KINGDOM),
      // canonical 2: Oenanthe
      iname("Oenanthe Vieillot, 1816", Rank.GENUS),
      iname("Oenanthe Pallas, 1771", Rank.GENUS),
      iname("Oenanthe L.", Rank.GENUS),
      // canonical 3: Oenanthe aquatica
      iname("Oenanthe aquatica", Rank.SPECIES),
      iname("Oenanthe aquatica Poir.", Rank.SPECIES),
      iname("Oenanthe aquatica Senser, 1957", Rank.SPECIES),
      // canonical 4: Natting tosee
      iname("Natting tosee", Rank.SPECIES),
      // canonical 5: Abies alba
      iname("Abies alba", Rank.SPECIES),
      iname("Abies alba Mumpf.", Rank.SPECIES),
      iname("Abies alba 1778", Rank.SPECIES),
      // canonical 6: Picea alba
      iname("Picea alba 1778", Rank.SPECIES),
      // canonical 7: Picea
      iname("Picea", Rank.GENUS),
      // canonical 8: Carex cayouettei
      iname("Carex cayouettei", Rank.SPECIES),
      // canonical 9: Carex comosa × Carex lupulina
      iname("Carex comosa × Carex lupulina", Rank.SPECIES),
      // canonical 10: Natting borealis (distinct epithets, not digit suffixes which name-parser v4 strips)
      iname("Natting borealis", Rank.SPECIES),
      // canonical 11: Natting montana
      iname("Natting montana", Rank.SPECIES),
      // canonical 12: Natting silvestris
      iname("Natting silvestris", Rank.SPECIES),
      // canonical 13: Natting palustris
      iname("Natting palustris", Rank.SPECIES),
      // canonical 14: Rodentia
      iname("Rodentia", Rank.GENUS),
      iname("Rodentia Bowdich, 1821", Rank.ORDER),
      // canonical 15: Aeropyrum coil-shaped virus
      iname("Aeropyrum coil-shaped virus", Rank.UNRANKED)
    ).forEach(n -> {
      ni.add(n);
    });
    dumpIndex();
    assertEquals(15, ni.size());
  }

  /**
   * Try to add the same name again and multiple names with the same key
   */
  @Test
  public void add() throws Exception {
    ni.add(create("Abies", "krösus-4-par∂atœs"));
    ni.add(create("Abies", "alba"));
    ni.add(create("Abies", "alba", "1873"));
    // single-tier: only the 2 distinct canonical names get an entry; the differently-dated
    // "Abies alba" collapses into the already existing canonical entry
    assertEquals(2, ni.size());

    // same canonical again, just with an author added - no new entry
    ni.add(create("Abies", "alba", "1873", "Miller"));
    assertEquals(2, ni.size());

    // a genuinely new canonical name
    ni.add(create("Abies", "perma", "1901", "Jones"));
    assertEquals(3, ni.size());
  }

  /**
   * The names index is meant to be single-tier: adding a ranked, authored name must only ever
   * create the one canonical entry - no separate rank/author specific child row.
   */
  @Test
  public void addIsSingleTier() throws Exception {
    // an authored, ranked name must still land as one canonical entry only
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setRank(Rank.SPECIES);
    n.setCombinationAuthorship(Authorship.authors("Mill."));
    n.rebuildScientificName();
    n.rebuildAuthorship();

    var m = ni.match(n, true, false);
    assertTrue(m.hasMatch());
    IndexName idx = m.getName();
    assertTrue("must be canonical", idx.isCanonical());
    assertEquals(idx.getKey(), idx.getCanonicalId());
    assertEquals(Rank.UNRANKED, idx.getRank());
    assertNull(idx.getAuthorship());
    // exactly one entry in the bucket, no separate child row.
    // the store key is the stemmed, normalized canonical: "alba" stems to "alb" (SciNameNormalizer)
    assertEquals(1, ni.store().get("abies alb").size());
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
    assertMatch(quest.getCanonicalNameKey(), n); // matches the weird canonical "?"

    // good infragenerics: single-tier collapses every "Tragulla" spelling (with/without authorship,
    // with/without a genus placement, at subgenus or section rank) onto one canonical uninomial
    // "Tragulla" - the canonical of an infrageneric name ignores its genus placement.
    n = new Name();
    n.setInfragenericEpithet("Tragulla");
    n.setRank(Rank.SUBGENUS);
    n.setType(NameType.SCIENTIFIC);
    var tragulla = assertInsert(n);
    final int tragullaKey = tragulla.getNameKey();

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
    var idx = ni.match(n, true, true);

    assertEquals(MatchType.NONE, idx.getType());
    assertEquals(origSize, ni.size());
  }

  private void assertMatch(int key, Name n) {
    final int origSize = ni.size();
    n.rebuildScientificName();
    var idx = ni.match(n, true, true);

    assertEquals(key, (int)idx.getNameKey());
    assertEquals(origSize, ni.size());
  }

  private NameMatch assertInsert(Name n) {
    final int origSize = ni.size();
    n.rebuildScientificName();
    var idx = ni.match(n, true, true);

    assertEquals(MatchType.EXACT, idx.getType());
    // new index can have 1 or 2 (canonical) records inserted
    assertTrue(ni.size() > origSize && ni.size() <= origSize+2);
    return idx;
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

    // single-tier: a ranked, authored name lands as its one canonical entry (key==canonicalId)
    NameMatch m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    final Integer idx = m.getName().getKey();
    final Integer cidx = m.getName().getCanonicalId();
    assertEquals(idx, cidx);
    assertEquals(1, ni.size());

    m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(1, ni.size());

    m = ni.match(n, true, false);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(1, ni.size());

    // a different authorship no longer creates a separate entry - the canonical string is unchanged,
    // so it resolves EXACT to the same single entry and never inserts a duplicate
    n.setAuthorship("Miller");
    n.setCombinationAuthorship(Authorship.authors("Miller"));
    m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(1, ni.size());

    n.setAuthorship("Tesla");
    n.setCombinationAuthorship(Authorship.authors("Tesla"));
    m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(cidx, m.getName().getCanonicalId());
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

    NameMatch m = ni.match(n1, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(n1.getScientificName(), m.getName().getScientificName());
    final int canonID = m.getName().getCanonicalId();
    final int key = m.getNameKey();
    // single-tier: the match is the canonical entry itself
    assertEquals(key, canonID);
    assertEquals(1, ni.size());

    // single-tier & canonical-only: the canonical name is built from the epithets (genus + specific +
    // infraspecific), so every rank / authorship / notho-marker / rank-marker spelling of the same
    // trinomial collapses onto the very same single canonical entry. No separate rank/author child
    // rows exist anymore, so each of these resolves EXACT to the same key and inserts nothing.

    // just a different rank
    m = matchNameCopy(n1, MatchType.EXACT, n -> n.setRank(Rank.VARIETY));
    assertNidx(m, key, canonID);

    // rank marker in the scientificName is irrelevant - canonical is built from the epithets
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.VARIETY);
      n.setScientificName("Abies alba var. alba");
    });
    assertNidx(m, key, canonID);

    // notho / hybrid marker is likewise stripped from the canonical
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.VARIETY);
      n.setScientificName("Abies alba × alba");
      n.setNotho(NamePart.INFRASPECIFIC);
    });
    assertNidx(m, key, canonID);

    // a yet different rank
    m = matchNameCopy(n1, MatchType.EXACT, n -> n.setRank(Rank.FORM));
    assertNidx(m, key, canonID);

    // a different authorship no longer matters
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.FORM);
      n.setCombinationAuthorship(Authorship.authors("Miller"));
      n.setScientificName("Abies alba f. alba");
      n.setAuthorship("Miller");
    });
    assertNidx(m, key, canonID);

    // no authorship
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.FORM);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
      n.setScientificName("Abies alba f. alba");
    });
    assertNidx(m, key, canonID);

    // no authorship, no rank -> the plain canonical query
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.UNRANKED);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
      n.setScientificName("Abies alba f. alba");
    });
    assertNidx(m, key, canonID);

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

    NameMatch m = ni.match(n1, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(n1.getScientificName(), m.getName().getScientificName());
    final int canonID = m.getName().getCanonicalId();
    final int key = m.getNameKey();
    // single-tier: the match is the canonical entry itself
    assertEquals(key, canonID);
    assertEquals(1, ni.size());

    // single-tier & canonical-only: the canonical of the uninomial "Puma" is the same whether the
    // name is given as a genus, a subgenus (infrageneric uninomial) or with any authorship, so all of
    // these collapse onto the same single canonical entry - no separate rank/author child rows.

    // given as a subgenus - same canonical uninomial "Puma"
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setInfragenericEpithet(n.getUninomial());
      n.setUninomial(null);
      n.setRank(Rank.SUBGENUS);
    });
    assertNidx(m, key, canonID);

    // a different authorship no longer matters
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setAuthorship("Linné");
      n.setCombinationAuthorship(Authorship.authors("Linné"));
    });
    assertNidx(m, key, canonID);

    // ranked, but no authorship
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.GENUS);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
    });
    assertNidx(m, key, canonID);

    // the plain canonical query
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.UNRANKED);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
    });
    assertNidx(m, key, canonID);

    assertEquals(1, ni.size());
  }

  private NameMatch matchNameCopy(Name original, MatchType matchTypeAssertion, Consumer<Name> modifier) {
    Name n = new Name(original);
    modifier.accept(n);
    NameMatch nm = ni.match(n, true, true);
    assertEquals("", matchTypeAssertion, nm.getType());
    return nm;
  }

  void assertNidx(NameMatch m, int nidx, int canonicalID){
    assertEquals(nidx, (int)m.getNameKey());
    assertEquals(canonicalID, (int)m.getName().getCanonicalId());
  }

  void dumpIndex() {
    ni.printIndex();
  }

  @Test
  public void getCanonical() throws Exception {
    ni.add(create("Abies", "alba", null, "Miller"));
    // single-tier: an authored species is reduced to its single canonical entry - no child row
    assertEquals(1, ni.size());

    IndexName n1 = ni.get(keyGen.get()-1);
    assertTrue(n1.isCanonical());
    assertEquals(n1.getKey(), n1.getCanonicalId());
    assertNull(n1.getAuthorship());
    assertEquals(Rank.UNRANKED, n1.getRank());
    // there are no non-canonical children, so the canonical has no group
    assertNull(ni.byCanonical(n1.getCanonicalId()));
  }

  @Test
  public void unparsed() throws Exception {
    assertEquals(0, ni.size());

    var names = Stream.of(
      // FLOW placeholder - 0 idxn
      Name.newBuilder()
          .scientificName("Aphaena dives var. [unnamed]")
          .authorship("Walker, 1851")
          .rank(Rank.SUBSPECIES)
          .type(NameType.PLACEHOLDER)
          .code(NomCode.ZOOLOGICAL),
      // ICTV virus (NameType.VIRUS dropped in name-parser v4.2; now OTHER + NomCode.VIRUS, indexed) - 4 idxn
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
      // GTDB OTU (NameType.OTU merged into OTHER in name-parser v4; OTHER is indexed) - 2 idxn each
      Name.newBuilder()
          .scientificName("AABM5-125-24")
          .rank(Rank.PHYLUM)
          .type(NameType.OTHER),
      Name.newBuilder()
          .scientificName("Aureabacteria_A")
          .rank(Rank.PHYLUM)
          .type(NameType.OTHER)
          .code(NomCode.BACTERIAL),
      // GTDB informal - 2 idxn each
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
      // GTDB no name (NameType.NO_NAME merged into OTHER in name-parser v4; OTHER is indexed) - 2 idxn
      Name.newBuilder()
          .scientificName("B3-LCP")
          .rank(Rank.CLASS)
          .type(NameType.OTHER),
      // VASCAN hybrid - 2 idxn
      Name.newBuilder()
          .scientificName("Agropyron cristatum × Agropyron fragile")
          .rank(Rank.SPECIES)
          .type(NameType.FORMULA)
          .code(NomCode.BOTANICAL)
    ).map(Name.Builder::build).collect(Collectors.toList());

    for (int repeat=1; repeat<3; repeat++) {
      for (Name n : names) {
        var m = ni.match(n, true, true);
        if (NameIndexImpl.INDEX_NAME_TYPES.contains(n.getType())) {
          assertTrue(m.hasMatch());
          assertNotNull(m.getName().getScientificName());
          // single-tier: every indexed name resolves to its one canonical entry (key==canonicalId)
          assertTrue(m.getName().isCanonical());
          assertEquals(m.getName().getKey(), m.getName().getCanonicalId());
        } else {
          assertFalse(m.hasMatch());
        }
      }
    }

    dumpIndex();
    // every type but PLACEHOLDER is indexed: 5 OTHER + 2 INFORMAL + 1 FORMULA names, 1 canonical idxn each
    assertEquals(8, ni.size());
  }

  private static IndexName create(String genus, String species){
    return create(genus, species, null);
  }
  private static IndexName create(String genus, String species, String year, String... authors){
    Name n = new Name();
    if (authors != null || year != null) {
      n.setCombinationAuthorship(Authorship.yearAuthors(year, authors));
    }
    n.setGenus(genus);
    n.setSpecificEpithet(species);
    n.setRank(Rank.SPECIES);
    n.rebuildScientificName();
    n.rebuildAuthorship();

    return new IndexName(n);
  }

  @Test
  public void insertNewNames() throws Exception {
    assertInsert("Larus fundatus", Rank.SPECIES);
    assertInsert("Puma concolor", Rank.SPECIES);
  }

  @Test
  public void UnrankedNames() throws Exception {
    // single-tier: "Biota" (with or without authorship, at any rank) is a single canonical entry.
    // The first spelling inserts it; every later spelling resolves EXACT to it without inserting.
    var m = assertInsert("Biota", Rank.DOMAIN);
    int key = m.getNameKey();
    assertEquals(1, ni.size());
    // "Biota End." has canonical "Biota" (End. is just authorship), so it matches the same entry
    m = assertCanonMatch(MatchType.EXACT, "Biota End.", Rank.GENUS);
    assertEquals(key, (int) m.getNameKey());
    m = assertCanonMatch(MatchType.EXACT, "Biota", Rank.UNRANKED);
    assertEquals(key, (int) m.getNameKey());
    assertEquals(1, ni.size());

    stop();

    setup();
    // order independence: starting from the unranked spelling yields the same single canonical entry
    m = assertInsert("Biota", Rank.UNRANKED);
    key = m.getNameKey();
    assertEquals(1, ni.size());
    m = assertCanonMatch(MatchType.EXACT, "Biota End.", Rank.GENUS);
    assertEquals(key, (int) m.getNameKey());
    m = assertCanonMatch(MatchType.EXACT, "Biota", Rank.DOMAIN);
    assertEquals(key, (int) m.getNameKey());
    assertEquals(1, ni.size());
  }
  
  @Test
  public void testLookup() throws Exception {
    addTestNames();

    // single-tier: authorship & rank are ignored - every spelling of a name resolves to its single
    // canonical entry (see the canonical keys noted in addTestNames)
    assertMatch(1, "Animalia", Rank.KINGDOM);

    assertMatch(14, "Rodentia", Rank.GENUS);
    assertCanonMatch(14, "Rodentia", Rank.ORDER);
    assertNoMatch("Rodenti", Rank.ORDER);

    // all authorship/year spellings of Rodentia collapse onto its canonical
    assertMatch(14, "Rodentia Bowdich, 1821", Rank.ORDER);
    assertMatch(14, "Rodentia Bowdich, 1?21", Rank.ORDER);
    assertMatch(14, "Rodentia Bowdich", Rank.ORDER);
    assertMatch(14, "Rodentia 1821", Rank.ORDER);
    assertMatch(14, "Rodentia Bow.", Rank.ORDER);
    assertMatch(14, "Rodentia Bow, 1821", Rank.ORDER);
    assertMatch(14, "Rodentia B 1821", Rank.ORDER);
    assertCanonMatch(14, "Rodentia", Rank.FAMILY);
    assertCanonMatch(14, "Rodentia Mill., 1823", Rank.SUBORDER);

    assertMatch(2, "Oenanthe", Rank.GENUS);
    assertMatch(2, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(2, "Oenanthe V", Rank.GENUS);
    assertMatch(2, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(2, "Oenanthe P", Rank.GENUS);
    assertMatch(2, "Oenanthe Pal", Rank.GENUS);
    assertMatch(2, "Œnanthe 1771", Rank.GENUS);
    assertMatch(2, "Œnanthe", Rank.GENUS);
    assertCanonMatch(2, "Oenanthe Camelot", Rank.GENUS);

    assertMatch(3, "Oenanthe aquatica", Rank.SPECIES);
    assertMatch(3, "Oenanthe aquatica Poir", Rank.SPECIES);
    assertMatch(3, "Œnanthe aquatica", Rank.SPECIES);

    // authorship no longer differentiates - all resolve to the single canonical Abies alba
    assertMatch(5, "Abies alba", Rank.SPECIES);
    assertMatch(5, "Abies alba Döring, 1778", Rank.SPECIES);
    assertMatch(5, "Abies alba Mumpf.", Rank.SPECIES);
    assertCanonMatch(5,"Abies alba Mill.", Rank.SPECIES);
    assertCanonMatch(5, "Abies alba 1789", Rank.SPECIES);

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
    List<IndexName> names = List.of(
      // canonical 1: Animalia
      iname("Animalia", Rank.KINGDOM),
      // canonical 2: Zyras (genus)
      iname("Zyras", Rank.GENUS),
      // canonical 2: Zyras (subgenus) - same canonical uninomial
      iname("Zyras", Rank.SUBGENUS),
      // canonical 3: Drusilla
      iname("Drusilla", Rank.GENUS),
      // canonical 4: Drusilla zyrasoides
      iname("Drusilla zyrasoides M.Dvořák, 1988", Rank.SPECIES),
      // canonical 5: Myrmedonia alternans (infrageneric dropped from the binomial canonical)
      iname("Myrmedonia (Zyras) alternans Cameron, 1925", Rank.SPECIES),
      // canonical 6: Myrmedonia bangae
      iname("Myrmedonia (Zyras) bangae Cameron, 1926", Rank.SPECIES),
      // canonical 7: Myrmedonia hirsutiventris
      iname("Myrmedonia (Zyras) hirsutiventris Champion, 1927", Rank.SPECIES),
      // canonical 8: Zyras alternans
      iname("Zyras (Zyras) alternans (Cameron, 1925)", Rank.SPECIES),
      // canonical 9: Zyras bangae
      iname("Zyras bangae (Cameron, 1926)", Rank.SPECIES)
    );
    ni.addAll(names);

    assertEquals(9, ni.size());
    assertEquals(1, (int) names.get(0).getKey());
    assertEquals("Zyras", names.get(2).getScientificName());

    assertMatch(4, "Drusilla zyrasoides", Rank.SPECIES);

    assertMatch(4, "Drusilla zyrasoides", Rank.SPECIES);
    assertCanonMatch(5, "Myrmedonia (Zyras) alternans", Rank.SPECIES);
    assertMatch(5, "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertMatch(5, "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertMatch(5, "Myrmedonia (Larus) alternans Cameron, 1925", Rank.SPECIES);
    // single-tier: a new authorship no longer creates a new entry - it matches the same canonical
    assertCanonMatch(5, "Myrmedonia alternans Krill, 1925", Rank.SPECIES);

    assertEquals(9, ni.size());
  }

  @Test
  public void stemming() throws Exception {
    for (Name n : NameIndexImplIT.prepareTestNames()) {
      var m = ni.match(n, true, true);
      System.out.println(m);
    }
    // single-tier: the test names are "Abies alba"/"Abies albus" with & without authorship. Stemming
    // folds the gender variants (alba/albus) into the same canonical bucket, so all of them collapse
    // onto a single canonical entry - which is the only entry, hence the only canonical.
    assertEquals(1, ni.size());
    int canonCnt = 0;
    for (var n : ni.all()){
      if (n.isCanonical()) canonCnt++;
    }
    assertEquals(1, canonCnt);
  }

  @Test
  public void testMissingAuthorBrackets() throws Exception {
    // single-tier: authorship (incl. bracket differences and different authors) is ignored -
    // every "Caretta caretta ..." resolves to the same single canonical entry with no authorship
    var m = ni.match(name("Caretta caretta Linnaeus", Rank.SPECIES), true, true);
    assertTrue(m.getName().isCanonical());
    assertNull(m.getName().getAuthorship());
    assertEquals(MatchType.EXACT, m.getType());
    final int key = m.getNameKey();
    assertEquals(1, ni.size());

    m = ni.match(name("Caretta caretta (Linnaeus)", Rank.SPECIES), true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(key, (int) m.getNameKey());
    assertNull(m.getName().getAuthorship());
    assertEquals(1, ni.size());

    m = ni.match(name("Caretta caretta (Peter)", Rank.SPECIES), true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(key, (int) m.getNameKey());
    assertNull(m.getName().getAuthorship());
    assertEquals(1, ni.size());
  }


  @Test
  public void testZooAuthors() throws Exception {
    assertEquals(0, ni.size());

    var m = ni.match(name("Chaetocnema belli Jacoby, 1904", Rank.SPECIES), true, true);
    assertTrue(m.getName().isCanonical());
    assertNull(m.getName().getAuthorship());
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(1, ni.size());

    // single-tier: "belli" and "bella" share the same stemmed canonical bucket ("bell") and
    // authorship is ignored, so this collapses onto the very same single canonical entry. The
    // canonical strings still differ ("belli" vs "bella"), so it is flagged as a VARIANT.
    var m2 = ni.match(name("Chaetocnema bella (Baly, 1876)", Rank.SPECIES), true, true);
    assertEquals(MatchType.VARIANT, m2.getType());
    assertEquals(m.getNameKey(), m2.getNameKey());
    assertNull(m2.getName().getAuthorship());
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
    // a differently-authored, differently-ranked spelling of the same canonical must match the same entry
    var m = ni.match(name("Abies alba", "L.", Rank.SUBSPECIES), false, false);
    assertTrue(m.hasMatch());
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(1, ni.size());
  }

  /**
   * A unicode spelling variant of an already indexed canonical name must still match it, as a VARIANT.
   */
  @Test
  public void unicodeVariantMatches() throws Exception {
    ni.match(name("Muller", null, Rank.GENUS), true, false);
    var m = ni.match(name("Müller", null, Rank.GENUS), false, false);
    assertTrue(m.hasMatch());
    assertEquals(MatchType.VARIANT, m.getType());
  }

  static IndexName iname(String name, Rank rank) throws InterruptedException {
    return new IndexName(name(name, rank));
  }

  private NameMatch assertCanonMatch(Integer key, String name, Rank rank) throws InterruptedException {
    // single-tier: a query that only differs from the stored canonical by rank/authorship is an EXACT
    // match to that single canonical entry (there is no separate CANONICAL match type anymore)
    NameMatch m = assertMatch(MatchType.EXACT, name, rank);
    assertTrue(m.hasMatch());
    assertTrue(m.getName().isCanonical());
    assertEquals(key, m.getName().getKey());
    return m;
  }
  
  private NameMatch assertNoMatch(String name, Rank rank) throws InterruptedException {
    NameMatch m = assertMatch(MatchType.NONE, name, rank);
    assertFalse(m.hasMatch());
    return m;
  }

  private NameMatch assertCanonMatch(MatchType expected, String name, Rank rank) throws InterruptedException {
    var m = assertMatch(expected, name, rank);
    assertTrue(m.getName().isCanonical());
    return m;
  }

  private NameMatch assertMatch(MatchType expected, String name, Rank rank) throws InterruptedException {
    NameMatch m = match(name, rank);
    if (expected != m.getType()) {
      System.out.println(m);
    }
    assertEquals("No match expected but got " + m.getType(),
        expected, m.getType()
    );
    return m;
  }

  private NameMatch assertMatch(int key, String name, Rank rank) throws InterruptedException {
    return assertMatch(key, null, name, rank);
  }

  private NameMatch assertMatch(int key, MatchType type, String name, Rank rank) throws InterruptedException {
    NameMatch m = match(name, rank);
    if (!m.hasMatch() || key != m.getName().getKey()) {
      System.err.println(m);
    }
    assertTrue("Expected single match but got none", m.hasMatch());
    assertEquals("Expected " + key + " but got " + m.getType(), key, (int) m.getName().getKey());
    if (type != null) {
      assertEquals("Expected " + type + " but got " + m.getType(), type, m.getType());
    }
    return m;
  }

  private NameMatch assertInsert(String name, Rank rank) throws InterruptedException {
    NameMatch m = ni.match(name(name, rank), false, false);
    assertNotEquals(MatchType.EXACT, m.getType());
    assertNotEquals(MatchType.VARIANT, m.getType());
    int cnt = ni.size();
    m = ni.match(name(name, rank), true, false);
    assertTrue(ni.size() > cnt);
    assertEquals(MatchType.EXACT, m.getType());
    return m;
  }
  
  private NameMatch match(String name, Rank rank) throws InterruptedException {
    NameMatch m = ni.match(name(name, rank), false, true);
    return m;
  }
  
}