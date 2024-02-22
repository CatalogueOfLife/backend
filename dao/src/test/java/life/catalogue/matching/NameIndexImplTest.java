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
import org.junit.Ignore;
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
    when(mapper.processAll()).thenReturn(new EmptyListCursor<>());
    doAnswer(new Answer<IndexName>() {
      public IndexName answer(InvocationOnMock invocation) {
        IndexName param = invocation.getArgument(0, IndexName.class);
        param.setKey(keyGen.getAndIncrement());
        return param;
      }}
    ).when(mapper).create(any());

    ni = NameIndexFactory.memory(factory, aNormalizer).started();
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
    List.of(
      // 1+2
      iname("Animalia", Rank.KINGDOM),
      // 3+4
      iname("Oenanthe Vieillot, 1816", Rank.GENUS),
      // 5
      iname("Oenanthe Pallas, 1771", Rank.GENUS),
      // 6
      iname("Oenanthe L.", Rank.GENUS),
      // 7+8
      iname("Oenanthe aquatica", Rank.SPECIES),
      // 9
      iname("Oenanthe aquatica Poir.", Rank.SPECIES),
      // 10
      iname("Oenanthe aquatica Senser, 1957", Rank.SPECIES),
      // 11+12
      iname("Natting tosee", Rank.SPECIES),
      // 13+14
      iname("Abies alba", Rank.SPECIES),
      // 15
      iname("Abies alba Mumpf.", Rank.SPECIES),
      // 16
      iname("Abies alba 1778", Rank.SPECIES),
      // 17+18
      iname("Picea alba 1778", Rank.SPECIES),
      // 19+20
      iname("Picea", Rank.GENUS),
      // 21+22
      iname("Carex cayouettei", Rank.SPECIES),
      // 23+24
      iname("Carex comosa × Carex lupulina", Rank.SPECIES),
      // 25+26
      iname("Natting tosee2", Rank.SPECIES),
      // 27+28
      iname("Natting tosee3", Rank.SPECIES),
      // 29+30
      iname("Natting tosee4", Rank.SPECIES),
      // 31+32
      iname("Natting tosee5", Rank.SPECIES),
      // 33+34
      iname("Rodentia", Rank.GENUS),
      // 35+36
      iname("Rodentia Bowdich, 1821", Rank.ORDER),
      // 37
      iname("Aeropyrum coil-shaped virus", Rank.UNRANKED)
    ).forEach(n -> {
      ni.add(n);
    });
    dumpIndex();
    assertEquals(36, ni.size());
  }

  /**
   * Try to add the same name again and multiple names with the same key
   */
  @Test
  public void add() throws Exception {
    ni.add(create("Abies", "krösus-4-par∂atœs"));
    ni.add(create("Abies", "alba"));
    ni.add(create("Abies", "alba", "1873"));
    // 2 canonical, 2 without and 1 with author
    assertEquals(5, ni.size());

    // one more with author, no new canonical
    ni.add(create("Abies", "alba", "1873", "Miller"));
    assertEquals(6, ni.size());

    // 2 new records
    ni.add(create("Abies", "perma", "1901", "Jones"));
    assertEquals(8, ni.size());
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
    n.setType(NameType.NO_NAME);
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

    // good infragenerics
    n = new Name();
    n.setInfragenericEpithet("Tragulla");
    n.setRank(Rank.SUBGENUS);
    n.setType(NameType.SCIENTIFIC);
    assertInsert(n);

    n = new Name();
    n.setInfragenericEpithet("Tragulla");
    n.setAuthorship("Nardo");
    n.setCombinationAuthorship(Authorship.authors("Nardo"));
    n.setRank(Rank.SUBGENUS);
    n.setType(NameType.SCIENTIFIC);
    assertInsert(n);

    n = new Name();
    n.setGenus("Triceps");
    n.setInfragenericEpithet("Tragulla");
    n.setAuthorship("Nardo");
    n.setCombinationAuthorship(Authorship.authors("Nardo"));
    n.setRank(Rank.SECTION_BOTANY);
    n.setType(NameType.SCIENTIFIC);
    assertInsert(n);
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

    NameMatch m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    final Integer idx = m.getName().getKey();
    final Integer cidx = m.getName().getCanonicalId();
    assertNotEquals(idx, cidx);
    assertEquals(2, ni.size());

    m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(2, ni.size());

    m = ni.match(n, true, false);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(2, ni.size());

    n.setAuthorship("Miller");
    n.setCombinationAuthorship(Authorship.authors("Miller"));
    m = ni.match(n, true, true);
    assertEquals(MatchType.VARIANT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(2, ni.size());

    n.setAuthorship("Tesla");
    n.setCombinationAuthorship(Authorship.authors("Tesla"));
    m = ni.match(n, true, true);
    final Integer idxTesla = m.getName().getKey();
    assertNotEquals(idx, cidx);
    assertEquals(MatchType.EXACT, m.getType());
    assertNotEquals(idx, idxTesla);
    assertEquals(cidx, m.getName().getCanonicalId());
    assertEquals(3, ni.size());
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
    final int m1Key = m.getNameKey();

    m = matchNameCopy(n1, MatchType.VARIANT, n -> {
      n.setRank(Rank.VARIETY);
    });
    assertNotEquals(m1Key, (int) m.getNameKey());
    assertCanonicalNidx(m, canonID);
    final int m2Key = m.getNameKey();

    // the scientificName is rebuilt if parsed, so this one is the exact same as above
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.VARIETY);
      n.setScientificName("Abies alba var. alba");
    });
    assertNidx(m, m2Key, canonID);

    m = matchNameCopy(n1, MatchType.VARIANT, n -> {
      n.setRank(Rank.VARIETY);
      n.setScientificName("Abies alba × alba");
      n.setNotho(NamePart.INFRASPECIFIC);
    });
    assertNidx(m, m2Key, canonID);
    final int m4Key = m.getNameKey();

    m = matchNameCopy(n1, MatchType.VARIANT, n -> {
      n.setRank(Rank.FORM);
    });
    assertNotEquals(m1Key, (int) m.getNameKey());
    assertNotEquals(m2Key, (int) m.getNameKey());
    assertCanonicalNidx(m, canonID);
    final int m5Key = m.getNameKey();

    m = matchNameCopy(n1, MatchType.VARIANT, n -> {
      n.setRank(Rank.FORM);
      n.setCombinationAuthorship(Authorship.authors("Miller")); // variant, we had Mill. before
      n.setScientificName("Abies alba f. alba");
      n.setAuthorship("Miller");
    });
    assertEquals(canonID, (int)m.getCanonicalNameKey());

    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.FORM);
      n.setCombinationAuthorship(Authorship.authors("Mill"));
      n.setScientificName("Abies alba f. alba");
      n.setAuthorship("Mill");
    });
    assertEquals(canonID, (int)m.getCanonicalNameKey());

    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.FORM);
      n.setAuthorship(null); // no authorship, but a real rank -> a new index name
      n.setCombinationAuthorship(null);
      n.setScientificName("Abies alba f. alba");
    });
    assertEquals(canonID, (int)m.getCanonicalNameKey());

    m = matchNameCopy(n1, MatchType.CANONICAL, n -> {
      n.setRank(Rank.UNRANKED);
      n.setAuthorship(null); // no authorship, no rank -> canonical
      n.setCombinationAuthorship(null);
      n.setScientificName("Abies alba f. alba");
    });

    assertNotEquals(m5Key, (int) m.getNameKey());
    assertCanonicalNidx(m, canonID);
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
    final int m1Key = m.getNameKey();

    // new insert when the name is given as a subgenus!
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setInfragenericEpithet(n.getUninomial());
      n.setUninomial(null);
      n.setRank(Rank.SUBGENUS);
    });
    assertNotEquals(m1Key, (int) m.getNameKey());
    assertCanonicalNidx(m, canonID);
    final int m2Key = m.getNameKey();

    m = matchNameCopy(n1, MatchType.VARIANT, n -> {
      n.setAuthorship("Linné");
      n.setCombinationAuthorship(Authorship.authors("Linné"));
    });
    assertNidx(m, m1Key, canonID);

    // we query with a canonical, but rank is not unranked
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.GENUS);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
    });
    assertCanonicalNidx(m, canonID);

    // this time its an exact match to the canonical
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.UNRANKED);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
    });
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

  void assertCanonicalNidx(NameMatch m, int canonicalID){
    assertEquals((int)m.getName().getCanonicalId(), canonicalID);
  }

  void dumpIndex() {
    System.out.println("Names Index:");
    ni.all().forEach(System.out::println);
  }

  @Test
  public void getCanonical() throws Exception {
    ni.add(create("Abies", "alba", null, "Miller"));
    assertEquals(2, ni.size());
    //
    IndexName n1 = ni.get(keyGen.get()-2);
    assertTrue(n1.isCanonical());
    IndexName n2 = ni.get(keyGen.get()-1);
    assertNotEquals(n1, n2);
    assertEquals(n1.getCanonicalId(), n2.getCanonicalId());
    assertEquals(n1.getKey(), n2.getCanonicalId());
    var group = ni.byCanonical(n1.getCanonicalId());
    assertEquals(1, group.size());
    assertEquals(n2, group.iterator().next());
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
      // ICTV virus - 4 idxn
      Name.newBuilder()
          .scientificName("Abutilon mosaic Bolivia virus")
          .rank(Rank.SPECIES)
          .type(NameType.VIRUS)
          .code(NomCode.VIRUS),
      Name.newBuilder()
          .scientificName("Abutilon mosaic Brazil virus")
          .rank(Rank.SPECIES)
          .type(NameType.VIRUS)
          .code(NomCode.VIRUS),
      // GTDB OTU - 4 idxn
      Name.newBuilder()
          .scientificName("AABM5-125-24")
          .rank(Rank.PHYLUM)
          .type(NameType.OTU),
      Name.newBuilder()
          .scientificName("Aureabacteria_A")
          .rank(Rank.PHYLUM)
          .type(NameType.OTU)
          .code(NomCode.BACTERIAL),
      // GTDB informal - 0 idxn
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
      // GTDB no name - 0 idxn
      Name.newBuilder()
          .scientificName("B3-LCP")
          .rank(Rank.CLASS)
          .type(NameType.NO_NAME),
      // VASCAN hybrid - 2 idxn
      Name.newBuilder()
          .scientificName("Agropyron cristatum × Agropyron fragile")
          .rank(Rank.SPECIES)
          .type(NameType.HYBRID_FORMULA)
          .code(NomCode.BOTANICAL)
    ).map(Name.Builder::build).collect(Collectors.toList());

    for (int repeat=1; repeat<3; repeat++) {
      for (Name n : names) {
        var m = ni.match(n, true, true);
        if (NameIndexImpl.INDEX_NAME_TYPES.contains(n.getType())) {
          assertTrue(m.hasMatch());
          assertNotNull(m.getName().getScientificName());
          final Integer idx = m.getName().getKey();
          final Integer cidx = m.getName().getCanonicalId();
          if (n.isCanonical()) {
            assertEquals(idx, cidx);
          } else {
            assertNotEquals(idx, cidx);
            var canon = ni.get(cidx);
            assertEquals(m.getName().getScientificName(), canon.getScientificName());
            assertNotEquals(m.getName().getRank(), canon.getRank()); // canonicals with OTU can only appear via rank normalisations
          }
        } else {
          assertFalse(m.hasMatch());
        }
      }
    }

    dumpIndex();
    assertEquals(14, ni.size());
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
    assertInsert("Biota", Rank.DOMAIN);
    assertInsert("Biota End.", Rank.GENUS);
    assertCanonMatch(MatchType.EXACT, "Biota", Rank.UNRANKED);

    stop();

    setup();
    assertInsert("Biota", Rank.UNRANKED);
    assertInsert("Biota End.", Rank.GENUS);
    assertInsert("Biota", Rank.DOMAIN);
  }
  
  @Test
  public void testLookup() throws Exception {
    addTestNames();

    assertMatch(2, "Animalia", Rank.KINGDOM);

    assertMatch(34, "Rodentia", Rank.GENUS);
    assertCanonMatch(33, "Rodentia", Rank.ORDER); // canonical match
    assertNoMatch("Rodenti", Rank.ORDER);
    
    assertMatch(35, "Rodentia Bowdich, 1821", Rank.ORDER);
    assertMatch(35, "Rodentia Bowdich, 1?21", Rank.ORDER);
    assertMatch(35, "Rodentia Bowdich", Rank.ORDER);
    assertMatch(35, "Rodentia 1821", Rank.ORDER);
    assertMatch(35, "Rodentia Bow.", Rank.ORDER);
    assertMatch(35, "Rodentia Bow, 1821", Rank.ORDER);
    assertMatch(35, "Rodentia B 1821", Rank.ORDER);
    assertCanonMatch(33, "Rodentia", Rank.FAMILY);
    assertCanonMatch(33, "Rodentia Mill., 1823", Rank.SUBORDER); // canonical match
    
    assertMatch(3, "Oenanthe", Rank.GENUS);
    assertMatch(4, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(4, "Oenanthe V", Rank.GENUS);
    assertMatch(4, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(5, "Oenanthe P", Rank.GENUS);
    assertMatch(5, "Oenanthe Pal", Rank.GENUS);
    assertMatch(5, "Œnanthe 1771", Rank.GENUS);
    assertMatch(3, "Œnanthe", Rank.GENUS);
    assertCanonMatch(3, "Oenanthe Camelot", Rank.GENUS);

    assertMatch(8, "Oenanthe aquatica", Rank.SPECIES);
    assertMatch(9, "Oenanthe aquatica Poir", Rank.SPECIES);
    assertMatch(8, "Œnanthe aquatica", Rank.SPECIES);
    
    // it is allowed to add an author to the single current canonical name if it doesnt have an author yet!
    assertMatch(14, "Abies alba", Rank.SPECIES);
    // matches the year!
    assertMatch(16, MatchType.VARIANT, "Abies alba Döring, 1778", Rank.SPECIES);
    assertMatch(15, "Abies alba Mumpf.", Rank.SPECIES);
    assertCanonMatch(13,"Abies alba Mill.", Rank.SPECIES);
    assertCanonMatch(13, "Abies alba 1789", Rank.SPECIES);

    // try unparsable names
    assertMatch(22, "Carex cayouettei", Rank.SPECIES);
    assertMatch(24, "Carex comosa × Carex lupulina", Rank.SPECIES);
    assertMatch(36, "Aeropyrum coil-shaped virus", Rank.UNRANKED);
    assertMatch(36, "Aeropyrum coil-shaped virus", Rank.SPECIES); // given in index as UNRANKED
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/451
   */
  @Test
  public void testSubgenusLookup() throws Exception {
    List<IndexName> names = List.of(
      //1+2
      iname("Animalia", Rank.KINGDOM),
      //3+4
      iname("Zyras", Rank.GENUS),
      //5
      iname("Zyras", Rank.SUBGENUS),
      //6+7
      iname("Drusilla", Rank.GENUS),
      //8+9
      iname("Drusilla zyrasoides M.Dvořák, 1988", Rank.SPECIES),
      //10+11
      iname("Myrmedonia (Zyras) alternans Cameron, 1925", Rank.SPECIES),
      //12+13
      iname("Myrmedonia (Zyras) bangae Cameron, 1926", Rank.SPECIES),
      //14+15
      iname("Myrmedonia (Zyras) hirsutiventris Champion, 1927", Rank.SPECIES),
      //16+17
      iname("Zyras (Zyras) alternans (Cameron, 1925)", Rank.SPECIES),
      //18+19
      iname("Zyras bangae (Cameron, 1926)", Rank.SPECIES)
    );
    ni.addAll(names);

    assertEquals(19, ni.size());
    assertEquals(2, (int) names.get(0).getKey());
    assertEquals("Zyras", names.get(2).getScientificName());

    assertMatch(8, "Drusilla zyrasoides", Rank.SPECIES);

    assertMatch(8, "Drusilla zyrasoides", Rank.SPECIES);
    assertCanonMatch(10, "Myrmedonia (Zyras) alternans", Rank.SPECIES);
    assertMatch(11, "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertMatch(11, "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertMatch(11, "Myrmedonia (Larus) alternans Cameron, 1925", Rank.SPECIES);
    assertInsert("Myrmedonia alternans Krill, 1925", Rank.SPECIES);

    assertEquals(20, ni.size());
  }

  @Test
  public void stemming() throws Exception {
    for (Name n : NameIndexImplIT.prepareTestNames()) {
      var m = ni.match(n, true, true);
      System.out.println(m);
    }
    assertEquals(3, ni.size());
    int canonCnt = 0;
    for (var n : ni.all()){
      if (n.isCanonical()) canonCnt++;
    }
    assertEquals(1, canonCnt);
  }

  @Test
  public void testMissingAuthorBrackets() throws Exception {
    var m = ni.match(name("Caretta caretta Linnaeus", Rank.SPECIES), true, true);
    assertEquals("Linnaeus", m.getName().getAuthorship());
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(2, ni.size());

    m = ni.match(name("Caretta caretta (Linnaeus)", Rank.SPECIES), true, true);
    assertEquals(MatchType.VARIANT, m.getType());
    assertEquals("Linnaeus", m.getName().getAuthorship());
    assertEquals(2, ni.size());

    m = ni.match(name("Caretta caretta (Peter)", Rank.SPECIES), true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals("(Peter)", m.getName().getAuthorship());
    assertEquals(3, ni.size());
  }


  @Test
  public void testZooAuthors() throws Exception {
    assertEquals(0, ni.size());

    var m = ni.match(name("Chaetocnema belli Jacoby, 1904", Rank.SPECIES), true, true);
    assertEquals("Jacoby, 1904", m.getName().getAuthorship());
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(2, ni.size());

    var m2 = ni.match(name("Chaetocnema bella (Baly, 1876)", Rank.SPECIES), true, true);
    assertEquals(MatchType.EXACT, m2.getType());
    assertNotEquals(m.getNameKey(), m2.getNameKey());
    assertEquals("(Baly, 1876)", m2.getName().getAuthorship());
    assertEquals(3, ni.size()); // same stemmed canonical
  }

  static Name name(String name, Rank rank) throws InterruptedException {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName());
    n.setRank(rank);
    return n;
  }

  static IndexName iname(String name, Rank rank) throws InterruptedException {
    return new IndexName(name(name, rank));
  }

  private NameMatch assertCanonMatch(Integer key, String name, Rank rank) throws InterruptedException {
    NameMatch m = assertMatch(MatchType.CANONICAL, name, rank);
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

  private NameMatch assertMatchAndInsert(MatchType type, String name, Rank rank) throws InterruptedException {
    NameMatch m = ni.match(name(name, rank), false, false);
    assertEquals(type, m.getType());
    m = ni.match(name(name, rank), true, false);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(rank, m.getName().getRank());
    assertEquals(name, m.getName().getScientificName());
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