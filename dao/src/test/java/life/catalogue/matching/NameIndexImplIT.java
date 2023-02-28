package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameIndexImplIT {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;

  NameIndex ni;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

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
      // 7
      iname("Oenanthe aquatica", Rank.SPECIES),
      // 8
      iname("Oenanthe aquatica Poir.", Rank.SPECIES),
      // 9
      iname("Oenanthe aquatica Senser, 1957", Rank.SPECIES),
      // 10
      iname("Natting tosee", Rank.SPECIES),
      // 11
      iname("Abies alba", Rank.SPECIES),
      // 12
      iname("Abies alba Mumpf.", Rank.SPECIES),
      // 13
      iname("Abies alba 1778", Rank.SPECIES),
      // 14+15
      iname("Picea alba 1778", Rank.SPECIES),
      // 16
      iname("Picea", Rank.GENUS),
      // 17
      iname("Carex cayouettei", Rank.SPECIES),
      // 18
      iname("Carex comosa × Carex lupulina", Rank.SPECIES),
      // 19
      iname("Natting tosee2", Rank.SPECIES),
      // 20
      iname("Natting tosee3", Rank.SPECIES),
      // 21
      iname("Natting tosee4", Rank.SPECIES),
      // 22
      iname("Natting tosee5", Rank.SPECIES),
      // 23
      iname("Rodentia", Rank.GENUS),
      // 24
      iname("Rodentia Bowdich, 1821", Rank.ORDER),
      // 25
      iname("Aeropyrum coil-shaped virus", Rank.UNRANKED)
    ).forEach(n -> {
      ni.add(n);
    });
    dumpIndex();
    assertEquals(25, ni.size());
  }

  void setupMemory(boolean erase) throws Exception {
    if (erase) {
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        session.getMapper(NamesIndexMapper.class).truncate();
      }
    }
    ni = NameIndexFactory.memory(SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started();
    if (erase) {
      assertEquals(0, ni.size());
    } else {
      assertEquals(4, ni.size());
    }
  }

  void setupPersistent(File location) throws Exception {
    ni = NameIndexFactory.persistent(location, SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started();
  }

  @Test
  public void loadApple() throws Exception {
    setupMemory(false);
    assertMatch(4, "Larus erfundus", Rank.SPECIES);
    assertMatch(4, "Larus erfunda", Rank.SPECIES);
    assertMatch(3, "Larus fusca", Rank.SPECIES);
    assertMatch(3, "Larus fuscus", Rank.SPECIES);
  }

  /**
   * Try to add the same name again and multiple names with the same key
   */
  @Test
  public void add() throws Exception {
    setupMemory(true);
    ni.add(create("Abies", "krösus-4-par∂atœs"));
    ni.add(create("Abies", "alba"));
    ni.add(create("Abies", "alba", "1873"));
    // 2 canonical, 1 with author
    assertEquals(3, ni.size());

    // one more with author, no new canonical
    ni.add(create("Abies", "alba", "1873", "Miller"));
    assertEquals(4, ni.size());

    // 2 new records
    ni.add(create("Abies", "perma", "1901", "Jones"));
    assertEquals(6, ni.size());
  }

  /**
   * match the same name over and over again to make sure we never insert duplicates
   */
  @Test
  public void avoidDuplicates() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

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
    setupMemory(true);

    Name n1 = new Name();
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

    m = matchNameCopy(n1, MatchType.EXACT, n -> {
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

    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.FORM);
    });
    assertNotEquals(m1Key, (int) m.getNameKey());
    assertNotEquals(m2Key, (int) m.getNameKey());
    assertCanonicalNidx(m, canonID);
    final int m5Key = m.getNameKey();

    m = matchNameCopy(n1, MatchType.VARIANT, n -> {
      n.setRank(Rank.FORM);
      n.setCombinationAuthorship(Authorship.authors("Miller"));
      n.setScientificName("Abies alba f. alba");
      n.setAuthorship("Miller");
    });

    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.FORM);
      n.setCombinationAuthorship(Authorship.authors("Mill"));
      n.setScientificName("Abies alba f. alba");
      n.setAuthorship("Mill");
    });

    m = matchNameCopy(n1, MatchType.CANONICAL, n -> {
      n.setRank(Rank.FORM);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
      n.setScientificName("Abies alba f. alba");
    });
    assertNotEquals(m5Key, (int) m.getNameKey());
    assertCanonicalNidx(m, canonID);
    final int m6Key = m.getNameKey();
  }

  @Test
  public void higher() throws Exception {
    setupMemory(true);

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

    m = matchNameCopy(n1, MatchType.EXACT, n -> {
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

    // we query with a canonical, hence exact
    m = matchNameCopy(n1, MatchType.EXACT, n -> {
      n.setRank(Rank.GENUS);
      n.setAuthorship(null);
      n.setCombinationAuthorship(null);
    });
    assertCanonicalNidx(m, canonID);

    // we query with a canonical, but rank differ. Not exact
    m = matchNameCopy(n1, MatchType.CANONICAL, n -> {
      n.setRank(Rank.SUBGENUS);
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

  List<Name> prepareTestNames() {
    Name n1 = new Name();
    n1.setScientificName("Abies alba");
    n1.setGenus("Abies");
    n1.setSpecificEpithet("alba");
    n1.setAuthorship("Mill.");
    n1.setCombinationAuthorship(Authorship.authors("Mill."));
    n1.setRank(Rank.SPECIES);
    n1.setType(NameType.SCIENTIFIC);

    Name n2 = new Name(n1); // should be a variant and no new index name
    n2.setSpecificEpithet("albus");
    n2.setScientificName("Abies albus");

    Name n3 = new Name(n1); // should be the canonical and no new index name
    n3.setAuthorship(null);
    n3.setCombinationAuthorship(null);

    Name n4 = new Name(n2); // should be the canonical and no new index name
    n4.setAuthorship(null);
    n4.setCombinationAuthorship(null);

    Name n5 = new Name(n1); // should be the same
    n1.setAuthorship("Miller");
    n1.setCombinationAuthorship(Authorship.authors("Miller"));

    return List.of(n1, n2, n3, n4, n5);
  }

  @Test
  public void sequentialMatching() throws Exception {
    var names = prepareTestNames();
    final int repeat = names.size()*2;

    sequentialMatching(names, repeat);

    // now also in reverse ordering
    var namesRev = new ArrayList<>(names);
    Collections.reverse(namesRev);
    sequentialMatching(namesRev, repeat);

    // manual order as failed in concurrent matching
    var namesAlt = List.of(names.get(2), names.get(1), names.get(0), names.get(4), names.get(3));
    sequentialMatching(namesAlt, repeat);
  }

  public void sequentialMatching(List<Name> rawNames, int repeat) throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

    final AtomicInteger counter = new AtomicInteger(0);

    for (int x=0; x<repeat; x++) {
      Name n = rawNames.get(x % rawNames.size());
      counter.incrementAndGet();
      var m = ni.match(n, true, true);
      assertTrue(m.hasMatch());
      final Integer idx = m.getName().getKey();
      final Integer cidx = m.getName().getCanonicalId();
      if (n.hasAuthorship()) {
        assertNotEquals(idx, cidx);
      } else {
        assertEquals(idx, cidx);
      }
    }

    dumpIndex();
    assertEquals(repeat, counter.get());
    assertEquals(2, ni.size());
    assertCanonicalAbiesAlba();
    ni.close();
  }

  /**
   * Try to add the same name concurrently, making sure we never get duplicates in the index
   */
  @Test
  public void concurrentMatching() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

    final List<Name> rawNames = prepareTestNames();
    final AtomicInteger counter = new AtomicInteger(0);
    ExecutorService exec = Executors.newFixedThreadPool(52, new NamedThreadFactory("test-matcher"));

    final int repeat = rawNames.size() * 60;
    StopWatch watch = StopWatch.createStarted();
    for (int x=0; x<repeat; x++) {
      Name n = rawNames.get(x % rawNames.size());
      CompletableFuture.supplyAsync(() -> {
        counter.incrementAndGet();
        return ni.match(n, true, true);
      }, exec).exceptionally(ex -> {
        ex.printStackTrace();
        return null;
      }).thenAccept(m -> {
        if (m == null) {
          fail("Matching error");
        }
        assertTrue(m.hasMatch());
        final Integer idx = m.getName().getKey();
        final Integer cidx = m.getName().getCanonicalId();
        if (n.hasAuthorship()) {
          assertNotEquals(idx, cidx);
        } else {
          assertEquals(idx, cidx);
        }
      });
    }
    ExecutorUtils.shutdown(exec);
    watch.stop();
    System.out.println(watch);

    dumpIndex();
    assertEquals(repeat, counter.get());
    assertEquals(2, ni.size());
    assertCanonicalAbiesAlba();
  }

  void dumpIndex() {
    //System.out.println("Names Index from memory:");
    //ni.all().forEach(System.out::println);
    System.out.println("\nNames Index from postgres:");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(NamesIndexMapper.class).processAll().forEach(System.out::println);
    }
  }

  @Test
  public void getCanonical() throws Exception {
    setupMemory(true);
    ni.add(create("Abies", "alba", null, "Miller"));
    assertEquals(2, ni.size());
    assertCanonicalAbiesAlba();
  }

  public void assertCanonicalAbiesAlba() throws Exception {
    IndexName n1 = ni.get(1);
    assertTrue(n1.isCanonical());
    IndexName n2 = ni.get(2);
    assertNotEquals(n1, n2);
    assertEquals(n1.getCanonicalId(), n2.getCanonicalId());
    assertEquals(n1.getKey(), n2.getCanonicalId());
    var group = ni.byCanonical(n1.getCanonicalId());
    assertEquals(1, group.size());
    assertEquals(n2, group.iterator().next());
  }

  @Test
  public void truncate() throws Exception {
    File fIdx = File.createTempFile("col", ".nidx");
    if (fIdx.exists()) {
      fIdx.delete();
    }
    setupPersistent(fIdx);
    ni.add(create("Abies", "alba", null, "Miller"));
    ni.add(create("Abies", "alba", null, "Duller"));
    assertEquals(7, ni.size());
    IndexName n = ni.get(7);
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Duller", n.getAuthorship());

    String epi = "alba";
    for (int i = 0; i<100; i++) {
      epi = StringUtils.increase(epi);
      ni.add(create("Abies", epi, ""+(1800+i)%200, "Döring"));
    }

    ni.reset();
    assertEquals(0, ni.size());

    ni.add(create("Abies", "alba", null, "Miller"));
    ni.add(create("Abies", "alba", null, "Duller"));
    n = ni.get(2);
    assertEquals(3, ni.size());
  }

  @Test
  public void unparsed() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

    var names = Stream.of(
      // FLOW placeholder
      Name.newBuilder()
          .scientificName("Aphaena dives var. [unnamed]")
          .authorship("Walker, 1851")
          .rank(Rank.SUBSPECIES)
          .type(NameType.PLACEHOLDER)
          .code(NomCode.ZOOLOGICAL),
      // ICTV virus
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
      // GTDB OTU
      Name.newBuilder()
          .scientificName("AABM5-125-24")
          .rank(Rank.PHYLUM)
          .type(NameType.OTU),
      Name.newBuilder()
          .scientificName("Aureabacteria_A")
          .rank(Rank.PHYLUM)
          .type(NameType.OTU)
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
      // GTDB no name
      Name.newBuilder()
          .scientificName("B3-LCP")
          .rank(Rank.CLASS)
          .type(NameType.NO_NAME),
      // VASCAN hybrid
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
          assertFalse(m.getName().isParsed());
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
    // 5 names inserted +2 canonicals because of phylum rank
    assertEquals(7, ni.size());
    ni.close();
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
  public void restart() throws Exception {
    File fIdx = File.createTempFile("col", ".nidx");
    if (fIdx.exists()) {
      fIdx.delete();
    }

    try {
      setupPersistent(fIdx);

      assertMatch(4, "Larus erfundus", Rank.SPECIES);

      System.out.println("RESTART");
      assertEquals(4, ni.size());
      ni.stop();
      ni.start();
      assertEquals(4, ni.size());
      assertMatch(4, "Larus erfundus", Rank.SPECIES);

      ni.stop();
      try {
        match("Larus erfundus", Rank.SPECIES);
        fail("Names index is closed and should not return");
      } catch (UnavailableException e) {
        // expected!
      }

    } finally {
      fIdx.delete();
    }
  }
  
  @Test
  public void insertNewNames() throws Exception {
    setupMemory(false);
    assertInsert("Larus fundatus", Rank.SPECIES);
    assertInsert("Puma concolor", Rank.SPECIES);
  }
  
  @Test
  public void testLookup() throws Exception {
    setupMemory(true);
    addTestNames();

    assertMatch(2, "Animalia", Rank.KINGDOM);

    assertMatch(23, "Rodentia", Rank.GENUS);
    assertNoMatch("Rodentia", Rank.ORDER);
    assertNoMatch("Rodenti", Rank.ORDER);
    
    assertMatch(24, "Rodentia Bowdich, 1821", Rank.ORDER);
    assertMatch(24, "Rodentia Bowdich, 1?21", Rank.ORDER);
    assertMatch(24, "Rodentia Bowdich", Rank.ORDER);
    assertMatch(24, "Rodentia 1821", Rank.ORDER);
    assertMatch(24, "Rodentia Bow.", Rank.ORDER);
    assertMatch(24, "Rodentia Bow, 1821", Rank.ORDER);
    assertMatch(24, "Rodentia B 1821", Rank.ORDER);
    assertNoMatch("Rodentia", Rank.FAMILY);
    assertNoMatch("Rodentia Mill., 1823", Rank.SUBORDER);
    
    assertMatch(3, "Oenanthe", Rank.GENUS);
    assertMatch(4, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(4, "Oenanthe V", Rank.GENUS);
    assertMatch(4, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(5, "Oenanthe P", Rank.GENUS);
    assertMatch(5, "Oenanthe Pal", Rank.GENUS);
    assertMatch(5, "Œnanthe 1771", Rank.GENUS);
    assertMatch(3, "Œnanthe", Rank.GENUS);
    assertCanonMatch(3, "Oenanthe Camelot", Rank.GENUS);

    assertMatch(7, "Oenanthe aquatica", Rank.SPECIES);
    assertMatch(8, "Oenanthe aquatica Poir", Rank.SPECIES);
    assertMatch(7, "Œnanthe aquatica", Rank.SPECIES);
    
    // it is allowed to add an author to the single current canonical name if it doesnt have an author yet!
    assertMatch(11, "Abies alba", Rank.SPECIES);
    assertMatch(13, "Abies alba Döring, 1778", Rank.SPECIES);
    assertMatch(12, "Abies alba Mumpf.", Rank.SPECIES);
    assertCanonMatch(11,"Abies alba Mill.", Rank.SPECIES);
    assertCanonMatch(11,"Abies alba 1789", Rank.SPECIES);

    // try unparsable names
    assertMatch(17, "Carex cayouettei", Rank.SPECIES);
    assertMatch(18, "Carex comosa × Carex lupulina", Rank.SPECIES);
    assertMatch(25, "Aeropyrum coil-shaped virus", Rank.UNRANKED);
    assertMatch(25, "Aeropyrum coil-shaped virus", Rank.SPECIES); // given in index as UNRANKED
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/451
   */
  @Test
  public void testSubgenusLookup() throws Exception {
    setupMemory(true);
    List<IndexName> names = List.of(
      //1+2
      iname("Animalia", Rank.KINGDOM),
      //3
      iname("Zyras", Rank.GENUS),
      //4
      iname("Zyras", Rank.SUBGENUS),
      //5
      iname("Drusilla", Rank.GENUS),
      //6+7
      iname("Drusilla zyrasoides M.Dvořák, 1988", Rank.SPECIES),
      //8+9
      iname("Myrmedonia (Zyras) alternans Cameron, 1925", Rank.SPECIES),
      //10+11
      iname("Myrmedonia (Zyras) bangae Cameron, 1926", Rank.SPECIES),
      //12+13
      iname("Myrmedonia (Zyras) hirsutiventris Champion, 1927", Rank.SPECIES),
      //14+15
      iname("Zyras (Zyras) alternans (Cameron, 1925)", Rank.SPECIES),
      //16+17
      iname("Zyras bangae (Cameron, 1926)", Rank.SPECIES)
    );
    ni.addAll(names);

    assertEquals(17, ni.size());
    assertEquals(2, (int) names.get(0).getKey());
    assertEquals("Zyras", names.get(2).getScientificName());

    assertMatch(6, "Drusilla zyrasoides", Rank.SPECIES);

    assertMatch(6, "Drusilla zyrasoides", Rank.SPECIES);
    assertMatch(8, MatchType.CANONICAL, "Myrmedonia (Zyras) alternans", Rank.SPECIES);
    assertMatch(8, MatchType.CANONICAL, "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertInsert("Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertInsert("Myrmedonia (Larus) alternans Cameron, 1925", Rank.SPECIES);
    assertInsert("Myrmedonia alternans Krill, 1925", Rank.SPECIES);

    assertEquals(20, ni.size());
  }

  @Test
  public void testMissingAuthorBrackets() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

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

  static Name name(String name, Rank rank) throws InterruptedException {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName());
    n.setRank(rank);
    return n;
  }

  static IndexName iname(String name, Rank rank) throws InterruptedException {
    return new IndexName(name(name, rank));
  }

  private NameMatch assertCanonMatch(Integer key, String name, Rank rank) throws InterruptedException {
    NameMatch m = assertMatchType(MatchType.CANONICAL, name, rank);
    assertTrue(m.hasMatch());
    assertEquals(key, m.getName().getKey());
    return m;
  }
  
  private NameMatch assertNoMatch(String name, Rank rank) throws InterruptedException {
    NameMatch m = assertMatchType(MatchType.NONE, name, rank);
    assertFalse(m.hasMatch());
    return m;
  }
  
  private NameMatch assertMatchType(MatchType expected, String name, Rank rank) throws InterruptedException {
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
    m = ni.match(name(name, rank), true, false);
    assertEquals(MatchType.EXACT, m.getType());
    return m;
  }
  
  private NameMatch match(String name, Rank rank) throws InterruptedException {
    NameMatch m = ni.match(name(name, rank), false, true);
    return m;
  }
  
}