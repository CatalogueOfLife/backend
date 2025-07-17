package life.catalogue.api;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.kryo.ApiKryoPool;

import org.gbif.dwc.terms.*;
import org.gbif.nameparser.api.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;

import com.esotericsoftware.kryo.Kryo;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import de.undercouch.citeproc.csl.CSLType;

/**
 * utility class to metrics new test instances to be used in tests.
 */
public class TestEntityGenerator {
  private final static Random RND = new Random();
  private final static Kryo kryo = new ApiKryoPool(1).create();
  private final static RandomInstance random = new RandomInstance();
  private static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();
  public static final AtomicInteger ID_GEN = new AtomicInteger(10000);

  public final static User USER_USER = new User();
  public final static User USER_EDITOR = new User();
  public final static User USER_ADMIN = new User();

  /**
   * Corresponds exactly to dataset record inserted via apple.sql or tree.sql with key=11
   */
  public final static Dataset DATASET11 = setUser(new Dataset());
  /**
   * Corresponds exactly to dataset record inserted via apple.sql with key=12
   */
  public final static Dataset DATASET12 = setUser(new Dataset());
  /**
   * Corresponds exactly to 1st name record inserted via apple.sql
   */
  public final static Name NAME1 = setUser(new Name());
  /**
   * Corresponds exactly to 2nd name record inserted via apple.sql
   */
  public final static Name NAME2 = setUser(new Name());
  /**
   * Corresponds exactly to 3rd name record inserted via apple.sql
   */
  public final static Name NAME3 = setUser(new Name());
  /**
   * Corresponds exactly to 4th name record inserted via apple.sql
   */
  public final static Name NAME4 = setUser(new Name());
  /**
   * Corresponds exactly to 1st taxon record inserted via apple.sql
   */
  public final static Taxon TAXON1 = setUser(new Taxon());
  /**
   * Corresponds exactly to 2nd taxon record inserted via apple.sql
   */
  public final static Taxon TAXON2 = setUser(new Taxon());
  /**
   * Corresponds exactly to 1st taxon record inserted via apple.sql
   */
  public final static Synonym SYN1 = setUser(new Synonym());
  /**
   * Corresponds exactly to 2nd taxon record inserted via apple.sql
   */
  public final static Synonym SYN2 = setUser(new Synonym());
  /**
   * Corresponds exactly to 1st reference record inserted via apple.sql
   */
  public final static Reference REF1 = setUser(new Reference());
  /**
   * Corresponds exactly to 2nd reference record inserted via apple.sql
   */
  public final static Reference REF1b = setUser(new Reference());
  public final static Reference REF2 = setUser(new Reference());

  public final static int VERBATIM_KEY1 = 1;
  public final static int VERBATIM_KEY2 = 2;
  public final static int VERBATIM_KEY3 = 3;
  public final static int VERBATIM_KEY4 = 4;
  public final static int VERBATIM_KEY5 = 5;
  // we keep the initial hashes of our test objects stored to compare if they have accidently changed during tests
  private final static int[] ORIGINAL_HASHES;
  private final static Entity<?>[] INSTANCES = new Entity[]{
    USER_USER, USER_EDITOR, USER_ADMIN,
    DATASET11, DATASET12,
    REF1, REF1b, REF2,
    NAME1, NAME2, NAME3, NAME4,
    TAXON1, TAXON2, SYN1, SYN2
  };

  static {
    USER_ADMIN.setKey(91);
    USER_ADMIN.setUsername("'admin'");
    USER_ADMIN.setFirstname("Stan");
    USER_ADMIN.setLastname("Sterling");
    USER_ADMIN.setEmail("stan@mailinator.com");
    USER_ADMIN.getRoles().add(User.Role.ADMIN);

    USER_EDITOR.setKey(92);
    USER_EDITOR.setUsername("editor");
    USER_EDITOR.setFirstname("Yuri");
    USER_EDITOR.setLastname("Roskov");
    USER_EDITOR.setEmail("yuri@mailinator.com");
    USER_EDITOR.getRoles().add(User.Role.EDITOR);

    USER_USER.setKey(93);
    USER_USER.setUsername("'user'");
    USER_USER.setFirstname("Frank");
    USER_USER.setLastname("Müller");
    USER_USER.setEmail("frank@mailinator.com");

    DATASET11.setKey(11);
    DATASET12.setKey(12);

    REF1.setId("ref-1");
    REF1.setCitation(REF1.getId());
    REF1.setDatasetKey(DATASET11.getKey());
    REF1.setCreatedBy(Users.DB_INIT);
    REF1.setModifiedBy(Users.DB_INIT);

    REF1b.setId("ref-1b");
    REF1b.setCitation(REF1b.getId());
    REF1b.setDatasetKey(DATASET11.getKey());
    REF1b.setCreatedBy(Users.DB_INIT);
    REF1b.setModifiedBy(Users.DB_INIT);

    REF2.setId("ref-2");
    REF2.setCitation(REF2.getId());
    REF2.setDatasetKey(DATASET11.getKey());
    REF2.setCreatedBy(Users.DB_INIT);
    REF2.setModifiedBy(Users.DB_INIT);

    NAME1.setId("name-1");
    NAME1.setDatasetKey(DATASET11.getKey());
    NAME1.setVerbatimKey(VERBATIM_KEY5);
    NAME1.setGenus("Malus");
    NAME1.setSpecificEpithet("sylvestris");
    NAME1.setRank(Rank.SPECIES);
    NAME1.setOrigin(Origin.SOURCE);
    NAME1.setType(NameType.SCIENTIFIC);
    NAME1.rebuildScientificName();
    NAME1.rebuildAuthorship();
    NAME1.setPublishedInId(REF1.getId());
    NAME1.setPublishedInPage("712");
    NAME1.setCreatedBy(Users.DB_INIT);
    NAME1.setModifiedBy(Users.DB_INIT);

    NAME2.setId("name-2");
    NAME2.setDatasetKey(DATASET11.getKey());
    NAME2.setGenus("Larus");
    NAME2.setSpecificEpithet("fuscus");
    NAME2.setRank(Rank.SPECIES);
    NAME2.setOrigin(Origin.SOURCE);
    NAME2.setType(NameType.SCIENTIFIC);
    NAME2.rebuildScientificName();
    NAME2.rebuildAuthorship();
    NAME2.setPublishedInId(null);
    NAME2.setPublishedInPage(null);
    NAME2.setCreatedBy(Users.DB_INIT);
    NAME2.setModifiedBy(Users.DB_INIT);

    NAME3.setId("name-3");
    NAME3.setDatasetKey(DATASET11.getKey());
    NAME3.setGenus("Larus");
    NAME3.setSpecificEpithet("fusca");
    NAME3.setRank(Rank.SPECIES);
    NAME3.setOrigin(Origin.SOURCE);
    NAME3.setType(NameType.SCIENTIFIC);
    NAME3.rebuildScientificName();
    NAME3.rebuildAuthorship();
    NAME3.setPublishedInId(null);
    NAME3.setPublishedInPage(null);
    NAME3.setCreatedBy(Users.DB_INIT);
    NAME3.setModifiedBy(Users.DB_INIT);

    NAME4.setId("name-4");
    NAME4.setDatasetKey(DATASET11.getKey());
    NAME4.setGenus("Larus");
    NAME4.setSpecificEpithet("erfundus");
    NAME4.setRank(Rank.SPECIES);
    NAME4.setOrigin(Origin.SOURCE);
    NAME4.setType(NameType.SCIENTIFIC);
    NAME4.rebuildScientificName();
    NAME4.rebuildAuthorship();
    NAME4.setPublishedInId(null);
    NAME4.setPublishedInPage(null);
    NAME4.setCreatedBy(Users.DB_INIT);
    NAME4.setModifiedBy(Users.DB_INIT);

    TAXON1.setId("root-1");
    TAXON1.setDatasetKey(DATASET11.getKey());
    TAXON1.setVerbatimKey(VERBATIM_KEY1);
    TAXON1.setName(NAME1);
    TAXON1.setStatus(TaxonomicStatus.ACCEPTED);
    TAXON1.setOrigin(Origin.SOURCE);
    TAXON1.setCreatedBy(Users.DB_INIT);
    TAXON1.setModifiedBy(Users.DB_INIT);

    TAXON2.setId("root-2");
    TAXON2.setDatasetKey(DATASET11.getKey());
    TAXON2.setVerbatimKey(VERBATIM_KEY5);
    TAXON2.setName(NAME2);
    TAXON2.setStatus(TaxonomicStatus.ACCEPTED);
    TAXON2.setOrigin(Origin.SOURCE);
    TAXON2.setExtinct(true);
    TAXON2.setTemporalRangeStart("Aalenian");
    TAXON2.setTemporalRangeEnd("Sinemurian");
    TAXON2.setCreatedBy(Users.DB_INIT);
    TAXON2.setModifiedBy(Users.DB_INIT);

    SYN1.setId("s1");
    SYN1.setName(NAME3);
    SYN1.setAccepted(TAXON2);
    SYN1.setStatus(TaxonomicStatus.SYNONYM);
    SYN1.setCreatedBy(Users.DB_INIT);
    SYN1.setModifiedBy(Users.DB_INIT);

    SYN2.setId("s2");
    SYN2.setName(NAME4);
    SYN2.setAccepted(TAXON2);
    SYN2.setStatus(TaxonomicStatus.SYNONYM);
    SYN2.setVerbatimKey(133);
    SYN2.setCreatedBy(Users.DB_INIT);
    SYN2.setModifiedBy(Users.DB_INIT);

    ORIGINAL_HASHES = buildHashes();
  }

  public static boolean hasObjectsChanged() {
    for (int i=0; i<INSTANCES.length; i++) {
      Entity<?> obj = INSTANCES[i];
      int hash = obj.hashCode();
      if (hash != ORIGINAL_HASHES[i]){
        System.err.println("Static " + obj.getClass().getSimpleName() + " test instance " + obj.getKey() + " has changed");
        return true;
      }
    }
    return false;
  }

  private static int[] buildHashes() {
    int[] hashes = new int[INSTANCES.length];
    for (int i=0; i<INSTANCES.length; i++) {
      hashes[i] = INSTANCES[i].hashCode();
    }
    return hashes;
  }

  /*
   * Creates a VernacularName using the specified vernacular name, belonging to the specified taxon and dataset DATASET11.
   */
  public static VernacularName newVernacularName(String name) {
    VernacularName vn = new VernacularName();
    vn.setName(name);
    vn.setLatin(name);
    vn.setLanguage("eng");
    vn.setCountry(Country.UNITED_KINGDOM);
    return vn;
  }

  public static VernacularName newVernacularName(String name, String lang) {
    VernacularName vn = new VernacularName();
    vn.setName(name);
    vn.setLatin(name);
    vn.setLanguage(lang);
    return vn;
  }
  
  public static Dataset newDataset(String title) {
    Dataset d = new Dataset();
    d.setPrivat(false);
    d.setTitle(title);
    d.setAlias(title);
    d.setType(DatasetType.TAXONOMIC);
    d.setLicense(License.CC0);
    d.setOrigin(DatasetOrigin.PROJECT);
    return d;
  }

  /*
   * Creates a new taxon with a generated id in apple test dataset 11
   */
  public static Taxon newTaxon() {
    return newTaxon("t" + ID_GEN.getAndIncrement());
  }
  
  public static Taxon newTaxon(Name n) {
    return newTaxon(n, "t" + ID_GEN.getAndIncrement(), null);
  }

  /*
   * Creates a new taxon with the specified id, belonging to dataset DATASET11.
   */
  public static Taxon newTaxon(String id) {
    return newTaxon(new Name(NAME1), id, TAXON1.getId());
  }

  /*
   * Creates a new taxon with the specified id, belonging to the specified dataset.
   */
  public static Taxon newTaxon(Name n, String id, String parentID) {
    Taxon t = setUserDate(new Taxon());
    t.setStatus(TaxonomicStatus.ACCEPTED);
    t.setScrutinizerDate(FuzzyDate.of(2010, 11, 24));
    t.setDatasetKey(n.getDatasetKey());
    t.setLink(URI.create("http://foo.com"));
    t.setExtinct(false);
    t.setId(id);
    t.setEnvironments(EnumSet.of(Environment.BRACKISH, Environment.FRESHWATER, Environment.TERRESTRIAL));
    t.setName(n);
    t.setNamePhrase("Foo");
    t.setOrigin(Origin.SOURCE);
    t.setParentId(parentID);
    t.setRemarks("Foo == Bar");
    return t;
  }
  
  public static Taxon newTaxon(int datasetKey, String scientificName) {
    return newTaxon(datasetKey, "t" + ID_GEN.incrementAndGet(), scientificName);
  }
  /*
   * Creates a new taxon with the specified id, belonging to the specified dataset.
   */
  public static Taxon newTaxon(int datasetKey, String id, String scientificName) {
    return newTaxon(datasetKey, id, TAXON1.getId(), Rank.SPECIES, scientificName);
  }

  public static Taxon newTaxon(int datasetKey, String id, String parentID, Rank rank, String scientificName) {
    Taxon t = newMinimalTaxon(datasetKey, id, parentID, rank, scientificName);
    // we don't set accordingTo as this is a read only field populated via reference
    //t.setAccordingTo("Foo");
    t.setScrutinizerDate(FuzzyDate.of(2010, 11, 24));
    t.setLink(URI.create("http://foo-bar.com"));
    t.setExtinct(true);
    t.setEnvironments(EnumSet.of(Environment.BRACKISH, Environment.FRESHWATER, Environment.TERRESTRIAL));
    t.setName(setUserDate(newName(datasetKey, id + "_name_id", scientificName, rank)));
    t.setRemarks("Foo != Bar");
    return t;
  }

  public static Taxon newMinimalTaxon(int datasetKey, String id, String parentID, Rank rank, String scientificName) {
    Taxon t = setUserDate(new Taxon());
    t.setDatasetKey(datasetKey);
    t.setId(id);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    t.setName(setUserDate(newMinimalName(datasetKey, id, scientificName, rank)));
    t.setOrigin(Origin.SOURCE);
    t.setParentId(parentID);
    return t;
  }

  public static CslData newCslData() {
    return (CslData) new RandomInstance().create(CslData.class, CslName.class, CslDate.class);
  }

  public static Synonym newSynonym(Taxon accepted) {
    Name n = newName(accepted.getDatasetKey(), "n-" + ID_GEN.getAndIncrement());
    return newSynonym(n, accepted.getId());
  }

  public static Synonym newSynonym(Name name, String acceptedID) {
    return newSynonym(TaxonomicStatus.SYNONYM, name, acceptedID);
  }

  public static Synonym newSynonym(TaxonomicStatus status, Name name, String acceptedID) {
    Synonym s = setUserDate(new Synonym());
    s.setDatasetKey(name.getDatasetKey());
    s.setId("syn" + ID_GEN.getAndIncrement());
    s.setName(name);
    // we don't set accordingTo as this is a read only field populated via reference
    //s.setAccordingTo("non Döring 1999");
    s.setStatus(status);
    s.setParentId(acceptedID);
    s.setOrigin(Origin.SOURCE);
    return s;
  }
  
  /**
   * Creates a new name instance with an id generated by the static id generator from this class which will not overlap
   */
  public static Name newName() {
    return newName("n" + ID_GEN.getAndIncrement());
  }
  
  /*
   * Creates a new name with the specified id, belonging to the specified dataset.
   */
  public static Name newName(String id) {
    return newName(id, RandomUtils.randomSpecies());
  }

  /*
   * Creates a new name with the specified id, belonging to the specified dataset.
   */
  public static Name newName(String id, String scientificName) {
    return newName(DATASET11.getKey(), id, scientificName);
  }

  /*
   * Creates a new name with the specified id, belonging to the specified dataset.
   */
  public static Name newName(int datasetKey, String id, String scientificName) {
    return newName(datasetKey, id, scientificName, Rank.SPECIES);
  }

  public static Name newName(int datasetKey, String id, String scientificName, Rank rank) {
    Name n = newMinimalName(datasetKey, id, scientificName, rank);
    n.setCombinationAuthorship(createAuthorship());
    if (RND.nextBoolean()) {
      n.setBasionymAuthorship(createAuthorship());
    }
    if (RND.nextInt(10) == 1) {
      n.setSanctioningAuthor("Fr.");
    }
    if (n.getSpecificEpithet() != null) {
      n.setInfragenericEpithet("Igen");
    }
    n.setCandidatus(true);
    n.setCultivarEpithet("Red Rose");
    n.setLink(URI.create("http://gbif.org"));
    n.setNotho(NamePart.SPECIFIC);
    n.setCode(NomCode.BOTANICAL);
    n.setNomStatus(NomStatus.ACCEPTABLE);
    n.setNomenclaturalNote("nom.illeg.");
    n.setUnparsed("debnnj$&%%");
    n.addRemarks("my first note");
    n.addRemarks("my second note");

    n.rebuildScientificName();
    n.rebuildAuthorship();
    return n;
  }

  public static Name newMinimalName(int datasetKey, String id, String scientificName, Rank rank) {
    Name n = setUserDate(new Name());
    n.setId(id);
    n.setDatasetKey(datasetKey);
    List<String> tokens = SPACE_SPLITTER.splitToList(scientificName);
    if (tokens.size() == 1) {
      n.setUninomial(tokens.get(0));
    } else {
      n.setGenus(tokens.get(0));
      n.setSpecificEpithet(tokens.get(1));
      if (tokens.size() > 2) {
        n.setInfraspecificEpithet(tokens.get(2));
      }
    }
    n.setRank(rank);
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);

    n.rebuildScientificName();
    n.rebuildAuthorship();
    return n;
  }

  public static List<Name> newNames(int size) {
    List<Name> names = Lists.newArrayList();
    while (size-- > 0) {
      names.add(newName());
    }
    return names;
  }
  
  public static Name newName(int datasetKey) {
    return newName(datasetKey, "_n_"+ID_GEN.incrementAndGet());
  }
  
  public static Name newName(int datasetKey, String id) {
    // prepare taxon to hook extensions to
    Name n = new Name();
    n.setId(id);
    n.setUninomial("Testomata");
    n.setRank(Rank.GENUS);
    n.setType(NameType.SCIENTIFIC);
    n.setOrigin(Origin.SOURCE);
    n.setDatasetKey(datasetKey);
    n.applyUser(Users.TESTER);

    n.rebuildScientificName();
    n.rebuildAuthorship();
    return n;
  }

  public static TypeMaterial newType(int datasetKey, String nameID) {
    return newType(datasetKey, "tm-" + ID_GEN.incrementAndGet(), nameID);
  }

  public static TypeMaterial newType(int datasetKey, String id, String nameID) {
    TypeMaterial tm = new TypeMaterial();
    tm.setDatasetKey(datasetKey);
    tm.setId(id);
    tm.setNameId(nameID);
    tm.setStatus(TypeStatus.HOLOTYPE);
    tm.setCitation("UGANDA: adult ♂, CW 21.5, CL 14.4, CH 7.4, FW 6.5 mm, Imatong Mountains, near border with South Sudan (3.79° N, 32.87° E), at 2,134 m asl, 11 Aug. 1955, L.C. Beadle (NHM 1955.11.8.26–27).");
    tm.setCountry(Country.UGANDA);
    tm.setLocality("Imatong Mountains, near border with South Sudan");
    tm.setLatitude("3° 47' 24''");
    tm.setLongitude("32° 52' 11.999''");
    tm.setCoordinate(new Coordinate(32.87, 3.79));
    tm.setAltitude("2134m");
    tm.setInstitutionCode("G-DC");
    tm.setCatalogNumber("1234");
    tm.setAssociatedSequences("genbank-seq-num");
    tm.setSex(Sex.FEMALE);
    tm.setDate("11 Aug. 1955");
    tm.setCollector("L.C. Beadle");
    tm.applyUser(Users.TESTER);
    return tm;
  }

  public static Taxon newTaxon(int datasetKey) {
    // prepare taxon to hook extensions to
    Taxon tax = new Taxon();
    tax.setId("_t_"+ID_GEN.incrementAndGet());
    tax.setName(newName(datasetKey));
    tax.setOrigin(Origin.SOURCE);
    tax.setStatus(TaxonomicStatus.ACCEPTED);
    tax.setDatasetKey(datasetKey);
    tax.applyUser(Users.TESTER);
    return tax;
  }

  public static Synonymy newSynonymy() {
    Synonymy s = new Synonymy();
    s.getHomotypic().addAll(newNames(1 + RND.nextInt(3)).stream().map(TestEntityGenerator::synonym).collect(Collectors.toList()));
    while (RND.nextBoolean() || RND.nextBoolean()) {
      s.getHeterotypic().addAll(newNames(1 + RND.nextInt(6)).stream().map(TestEntityGenerator::synonym).collect(Collectors.toList()));
    }
    return s;
  }

  private static Synonym synonym(Name n) {
    var s = new Synonym();
    s.setName(n);
    s.setStatus(TaxonomicStatus.SYNONYM);
    return s;
  }

  public static Reference newReference() {
    var ref = newReference(RandomUtils.randomLatinString(25));
    CslName author = new CslName("Harry Mulisch the Greatest");
    ref.getCsl().setAuthor(
      ArrayUtils.add(ref.getCsl().getAuthor(), author)
    );
    return ref;
  }

  public static Reference newReference(String title) {
    return newReference(title, "John", "Smith", "Betty", "Jones");
  }

  public static Reference newReference(String title, String... authorParts) {
    Reference r = setUserDate(new Reference());
    r.setId("r" + ID_GEN.getAndIncrement());
    r.setDatasetKey(TestEntityGenerator.DATASET11.getKey());
    CslData csl = new CslData();
    r.setCsl(csl);
    csl.setType(CSLType.ARTICLE_JOURNAL);
    csl.setTitle(title);
    csl.setContainerTitle("Nature");
    csl.setVolume("556");
    csl.setAbstrct("a very long article you should read");
    List<CslName> authors = new ArrayList<>();
    for (int idx = 0; idx < authorParts.length; idx = idx + 2) {
      CslName author = new CslName();
      author.setGiven(authorParts[idx]);
      author.setFamily(authorParts[idx + 1]);
      author.setNonDroppingParticle("de");
      authors.add(author);
    }
    csl.setAuthor(authors.toArray(new CslName[0]));
    CslDate date = new CslDate();
    date.setDateParts(new int[][] {{2014, 8, 12}});
    date.setLiteral("2014-8-12");
    csl.setAccessed(date);
    csl.setCategories(new String[] {"A", "B", "C"});
    r.setCitation(CslUtil.buildCitation(csl));
    return r;
  }

  public static Authorship createAuthorship() {
    Authorship a = Authorship.yearAuthors(RandomUtils.randomSpeciesYear(), RandomUtils.randomAuthor());
    while (RND.nextBoolean()) {
      a.addAuthor(RandomUtils.randomAuthor());
    }
    return a;
  }

  public static CslData createCsl() {
    CslData csl = (CslData) random.create(CslData.class, CslName.class, CslDate.class);
    csl.getOriginalDate().setDateParts(new int[][] {{1752, 4, 4}, {1752, 8, 4}});
    csl.getSubmitted().setDateParts(new int[][] {{1850, 6, 12}});
    csl.setURL("http://gbif.org");
    csl.setDOI("10.1093/database/baw125");
    csl.setISSN("1758-0463");
    return csl;
  }

  public static VerbatimRecord createVerbatim() {
    VerbatimRecord rec = new VerbatimRecord(11, "myFile.txt", DwcTerm.Taxon);
    rec.setDatasetKey(TestEntityGenerator.DATASET11.getKey());
    for (Term t : DwcTerm.values()) {
      rec.put(t, RandomUtils.randomLatinString(1 + RND.nextInt(23)).toLowerCase());
    }
    for (Term t : DcTerm.values()) {
      rec.put(t, RandomUtils.randomUnicodeString(1 + RND.nextInt(77)));
    }
    for (Term t : GbifTerm.values()) {
      rec.put(t, RandomUtils.randomLatinString(1 + RND.nextInt(8)));
    }
    rec.put(UnknownTerm.build("http://col.plus/terms/punk"),
        RandomUtils.randomLatinString(500 + RND.nextInt(2000)));
    rec.put(UnknownTerm.build("Col_name"), RandomUtils.randomSpecies());
    rec.addIssue(Issue.ACCEPTED_NAME_MISSING);
    rec.addIssue(Issue.NAME_VARIANT);
    return rec;
  }

  public static SimpleNameLink newSimpleNameWithoutStatusParent() {
    SimpleNameLink sn = newSimpleName(RandomUtils.randomLatinString(5));
    sn.setStatus(null);
    sn.setParent(null);
    return sn;
  }

  public static SimpleNameLink newSimpleName() {
    return newSimpleName(RandomUtils.randomLatinString(5));
  }

  public static SimpleNameLink newSimpleName(String id) {
    SimpleNameLink n = new SimpleNameLink();
    n.setId(id);
    n.setName(RandomUtils.randomSpecies());
    n.setAuthorship(RandomUtils.randomAuthorship().toString());
    n.setRank(Rank.SPECIES);
    n.setStatus(TaxonomicStatus.ACCEPTED);
    n.setParent(RandomUtils.randomGenus());
    n.setCode(NomCode.ZOOLOGICAL);
    return n;
  }

  public static NameUsageWrapper newNameUsageTaxonWrapper() {
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(new Taxon(TAXON1));
    nuw.getUsage().setSectorMode(Sector.Mode.MERGE);
    nuw.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.NAME_VARIANT, Issue.DISTRIBUTION_AREA_INVALID));
    nuw.setSectorPublisherKey(UUID.randomUUID());
    nuw.setSecondarySourceGroups(Set.of(InfoGroup.AUTHORSHIP, InfoGroup.PUBLISHED_IN));
    nuw.setSecondarySourceKeys(Set.of(1010,  2123));
    return copy(nuw);
  }

  public static NameUsageWrapper newNameUsageTaxonWrapperComplete() {
    var nuw = newNameUsageTaxonWrapper();
    nuw.setGroup(TaxGroup.Algae);
    nuw.setPublisherKey(UUID.randomUUID());
    nuw.setSectorDatasetKey(4567);
    nuw.setDecisions(new ArrayList<>(List.of( // array list to avoid kryo not doing a deep copy
      new SimpleDecision(66, 456, EditorialDecision.Mode.UPDATE),
      new SimpleDecision(62, 456, EditorialDecision.Mode.BLOCK)
    )));
    nuw.setClassification(new ArrayList<>(List.of(
      SimpleName.sn("Karambula"),
      SimpleName.sn(Rank.ORDER, "Karambulales"),
      SimpleName.sn("d4f", Rank.PHYLUM, "Karambulatae", null) // authorship is not kept !!!
    )));
    var u = (Taxon) nuw.getUsage();
    u.setParentId("P68");
    u.setSectorKey(12);
    u.setScrutinizerID("456ZT");
    u.setExtinct(true);
    u.setScrutinizer("drftg");
    u.setScrutinizerDate(FuzzyDate.of("2008-08"));
    u.setEnvironments(Set.of(Environment.TERRESTRIAL, Environment.MARINE));
    u.setTemporalRangeStart("start");
    u.setTemporalRangeEnd("end");
    u.setTemporalRangeStart(GeoTime.byName("Neoarchean"));
    u.setTemporalRangeEnd(GeoTime.byName("Cretaceous"));
    u.setOrdinal(789);
    u.setNamePhrase("ftgzhj");
    u.setLink(URI.create("http://go.to/me"));
    u.setRemarks("drfthuj gtzhu jz7ghu");
    u.setIdentifier(List.of(
      Identifier.parse("col:4r56"),
      Identifier.parse("gbif:3456789")
    ));
    var n = u.getName();
    n.setSectorKey(13);
    n.setSectorMode(Sector.Mode.MERGE);
    n.setGender(Gender.FEMININE);
    n.setGenderAgreement(true);
    n.setNomStatus(NomStatus.ESTABLISHED);
    n.setPublishedInPage("14");
    n.setPublishedInYear(1988);
    n.setLink(URI.create("http://read.me"));
    n.setRemarks("NR guzgqszugwqtfdwqw");
    n.setEtymology("etym");
    n.setNomenclaturalNote("nom notes not good");
    return  nuw;
  }

  public static NameUsageWrapper newNameUsageSynonymWrapper() {
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(new Synonym(SYN2));
    EnumSet<Issue> issues = EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.NAME_VARIANT,
        Issue.DISTRIBUTION_AREA_INVALID);
    nuw.setIssues(issues);
    return copy(nuw);
  }

  /**
   * Deep copies objects using Kryo.
   * Object classes to be copied need to be registered with the ApiKryoPool.
   * @param obj
   * @param <T>
   * @return a deep copy of obj
   */
  public static <T> T copy(T obj) {
    return kryo.copy(obj);
  }

  public static NameUsageWrapper newNameUsageBareNameWrapper() {
    NameUsageWrapper nuw = new NameUsageWrapper();
    BareName bn = new BareName();
    bn.setName(NAME4);
    nuw.setUsage(bn);
    EnumSet<Issue> issues = EnumSet.of(Issue.ID_NOT_UNIQUE);
    nuw.setIssues(issues);
    return copy(nuw);
  }
  
  public static EditorialDecision newDecision(int datasetKey, int subjectDatasetKey, String id) {
    EditorialDecision d = new EditorialDecision();
    d.setDatasetKey(datasetKey);
    d.setSubjectDatasetKey(subjectDatasetKey);
    d.setSubject(newSimpleName(id));
    d.setMode(EditorialDecision.Mode.REVIEWED);
    d.setNote("generated by tests");
    setUser(d);
    return d;
  }
  
  public static <T extends UserManaged> List<T> nullifyDate(List<T> managed) {
    for (T m : managed) {
      nullifyDate(m);
    }
    return managed;
  }

  public static Taxon nullifyDate(Taxon taxon) {
    nullifyDate((UserManaged) taxon);
    nullifyDate(taxon.getName());
    return taxon;
  }

  public static Synonym nullifyDate(Synonym syn) {
    nullifyDate((UserManaged) syn);
    nullifyDate(syn.getName());
    return syn;
  }

  public static <T extends UserManaged> T nullifyDate(T managed) {
    managed.setCreated(null);
    managed.setModified(null);
    return managed;
  }

  public static <T extends UserManaged> void nullifyDate(Collection<T> managed) {
    managed.forEach(TestEntityGenerator::nullifyDate);
  }

  public static Taxon nullifyUserDate(Taxon taxon) {
    if (taxon != null) {
      nullifyUserDate((UserManaged) taxon);
      nullifyUserDate(taxon.getName());
    }
    return taxon;
  }

  public static Synonym nullifyUserDate(Synonym syn) {
    nullifyUserDate((UserManaged) syn);
    nullifyUserDate(syn.getName());
    nullifyUserDate(syn.getAccepted());
    return syn;
  }

  public static <T extends UserManaged> T nullifyUserDate(T managed) {
    if (managed != null) {
      managed.setCreated(null);
      managed.setCreatedBy(null);
      managed.setModified(null);
      managed.setModifiedBy(null);
    }
    return managed;
  }

  public static <T extends UserManaged> Collection<T> nullifyUserDate(Collection<T> managed) {
    managed.forEach(TestEntityGenerator::nullifyUserDate);
    return managed;
  }

  public static <T extends UserManaged> T setUser(T managed) {
    managed.applyUser(Users.DB_INIT);
    return managed;
  }

  public static <T extends UserManaged> T setUserDate(T managed) {
    return setUserDate(managed, Users.DB_INIT);
  }

  public static <T extends UserManaged> T setUserDate(T managed, Integer userKey) {
    return setUserDate(managed, userKey, LocalDateTime.now());
  }

  public static <T extends UserManaged> T setUserDate(T managed, Integer userKey, LocalDateTime dateTime) {
    managed.setCreated(dateTime);
    managed.setCreatedBy(userKey);
    managed.setModified(dateTime);
    managed.setModifiedBy(userKey);
    return managed;
  }
  
}
