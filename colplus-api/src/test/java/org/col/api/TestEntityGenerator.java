package org.col.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.gbif.dwc.terms.*;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

/**
 * utility class to metrics new test instances to be used in tests.
 */
public class TestEntityGenerator {

  private final static Random RND = new Random();
  private final static RandomInstance random = new RandomInstance();
  private static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();

  /**
   * Corresponds exactly to dataset record inserted via apple.sql with key=11
   */
  public final static Dataset DATASET1 = new Dataset();
  /**
   * Corresponds exactly to dataset record inserted via apple.sql with key=12
   */
  public final static Dataset DATASET2 = new Dataset();
  /**
   * Corresponds exactly to 1st name record inserted via apple.sql
   */
  public final static Name NAME1 = new Name();
  /**
   * Corresponds exactly to 2nd name record inserted via apple.sql
   */
  public final static Name NAME2 = new Name();
  /**
   * Corresponds exactly to 3rd name record inserted via apple.sql
   */
  public final static Name NAME3 = new Name();
  /**
   * Corresponds exactly to 4th name record inserted via apple.sql
   */
  public final static Name NAME4 = new Name();
  /**
   * Corresponds exactly to 1st taxon record inserted via apple.sql
   */
  public final static Taxon TAXON1 = new Taxon();
  /**
   * Corresponds exactly to 2nd taxon record inserted via apple.sql
   */
  public final static Taxon TAXON2 = new Taxon();
  /**
   * Corresponds exactly to 1st taxon record inserted via apple.sql
   */
  public final static Synonym SYN1 = new Synonym();
  /**
   * Corresponds exactly to 2nd taxon record inserted via apple.sql
   */
  public final static Synonym SYN2 = new Synonym();
  /**
   * Corresponds exactly to 1st reference record inserted via apple.sql
   */
  public final static Reference REF1 = new Reference();
  /**
   * Corresponds exactly to 2nd reference record inserted via apple.sql
   */
  public final static Reference REF2 = new Reference();

  static {
    DATASET1.setKey(11);
    DATASET2.setKey(12);

    REF1.setKey(1);
    REF1.setId("ref-1");
    REF1.setDatasetKey(DATASET1.getKey());

    REF2.setKey(2);
    REF2.setId("ref-2");
    REF2.setDatasetKey(DATASET2.getKey());

    NAME1.setKey(1);
    NAME1.setHomotypicNameKey(1);
    NAME1.setId("name-1");
    NAME1.setDatasetKey(DATASET1.getKey());
    NAME1.setGenus("Malus");
    NAME1.setSpecificEpithet("sylvestris");
    NAME1.setRank(Rank.SPECIES);
    NAME1.setOrigin(Origin.SOURCE);
    NAME1.setType(NameType.SCIENTIFIC);
    NAME1.updateScientificName();
    NAME1.setPublishedInKey(REF1.getKey());
    NAME1.setPublishedInPage("712");

    NAME2.setKey(2);
    NAME2.setHomotypicNameKey(2);
    NAME2.setId("name-2");
    NAME2.setDatasetKey(DATASET1.getKey());
    NAME2.setGenus("Larus");
    NAME2.setSpecificEpithet("fuscus");
    NAME2.setRank(Rank.SPECIES);
    NAME2.setOrigin(Origin.SOURCE);
    NAME2.setType(NameType.SCIENTIFIC);
    NAME2.updateScientificName();
    NAME2.setPublishedInKey(null);
    NAME2.setPublishedInPage(null);

    NAME3.setKey(3);
    NAME3.setHomotypicNameKey(2);
    NAME3.setId("name-3");
    NAME3.setDatasetKey(DATASET1.getKey());
    NAME3.setGenus("Larus");
    NAME3.setSpecificEpithet("fusca");
    NAME3.setRank(Rank.SPECIES);
    NAME3.setOrigin(Origin.SOURCE);
    NAME3.setType(NameType.SCIENTIFIC);
    NAME3.updateScientificName();
    NAME3.setPublishedInKey(null);
    NAME3.setPublishedInPage(null);

    NAME4.setKey(4);
    NAME4.setHomotypicNameKey(4);
    NAME4.setId("name-4");
    NAME4.setDatasetKey(DATASET1.getKey());
    NAME4.setGenus("Larus");
    NAME4.setSpecificEpithet("erfundus");
    NAME4.setRank(Rank.SPECIES);
    NAME4.setOrigin(Origin.SOURCE);
    NAME4.setType(NameType.SCIENTIFIC);
    NAME4.updateScientificName();
    NAME4.setPublishedInKey(null);
    NAME4.setPublishedInPage(null);

    TAXON1.setKey(1);
    TAXON1.setId("root-1");
    TAXON1.setDatasetKey(DATASET1.getKey());
    TAXON1.setName(NAME1);
    TAXON1.setOrigin(Origin.SOURCE);

    TAXON2.setKey(2);
    TAXON2.setId("root-2");
    TAXON2.setDatasetKey(DATASET1.getKey());
    TAXON2.setName(NAME2);
    TAXON2.setOrigin(Origin.SOURCE);

    SYN1.setName(NAME3);
    SYN1.setAccepted(TAXON2);
    SYN1.setStatus(TaxonomicStatus.SYNONYM);

    SYN2.setName(NAME4);
    SYN2.setAccepted(TAXON2);
    SYN2.setStatus(TaxonomicStatus.SYNONYM);
  }

  /*
   * Creates a VernacularName using the specified vernacular name, belonging to the specified taxon
   * and dataset DATASET1.
   */
  public static VernacularName newVernacularName(String name) {
    VernacularName vn = new VernacularName();
    vn.setName(name);
    vn.setLatin(name);
    vn.setLanguage(Language.ENGLISH);
    vn.setCountry(Country.UNITED_KINGDOM);
    return vn;
  }

  /*
   * Creates a new taxon with the specified id, belonging to dataset DATASET1.
   */
  public static Taxon newTaxon(String id) {
    return newTaxon(DATASET1.getKey(), id);
  }

  /*
   * Creates a new taxon with the specified id, belonging to the specified dataset.
   */
  public static Taxon newTaxon(int datasetKey, String id) {
    Taxon t = new Taxon();
    t.setAccordingTo("Foo");
    t.setAccordingToDate(LocalDate.of(2010, 11, 24));
    t.setDatasetKey(datasetKey);
    t.setDatasetUrl(URI.create("http://foo.com"));
    t.setFossil(true);
    t.setId(id);
    t.setLifezones(EnumSet.of(Lifezone.BRACKISH, Lifezone.FRESHWATER, Lifezone.TERRESTRIAL));
    t.setName(NAME1);
    t.setOrigin(Origin.SOURCE);
    t.setParentKey(TAXON1.getKey());
    t.setRecent(true);
    t.setRemarks("Foo == Bar");
    t.setSpeciesEstimate(81);
    t.setSpeciesEstimateReferenceKey(REF1.getKey());
    return t;
  }

  public static CslData newCslData() {
    return (CslData) new RandomInstance().create(CslData.class, CslName.class, CslDate.class);
  }

  /*
   * Creates a new taxon with the specified id, belonging to the specified dataset.
   */
  public static Synonym newSynonym(TaxonomicStatus status, Name name, Taxon accepted) {
    Synonym s = new Synonym();
    s.setName(name);
    s.setAccordingTo("non Döring 1999");
    s.setStatus(status);
    s.setAccepted(accepted);
    return s;
  }

  /*
   * Creates a new name with the specified id, belonging to the specified dataset.
   */
  public static Name newName(String id) {
    Name n = newName();
    n.setId(id);
    return n;
  }

  public static Name newName() {
    Name n = new Name();
    n.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    n.setCombinationAuthorship(createAuthorship());
    if (RND.nextBoolean()) {
      n.setBasionymAuthorship(createAuthorship());
    }
    if (RND.nextInt(10) == 1) {
      n.setSanctioningAuthor("Fr.");
    }
    List<String> tokens = SPACE_SPLITTER.splitToList(RandomUtils.randomSpecies());
    n.setGenus(tokens.get(0));
    n.setSpecificEpithet(tokens.get(1));
    n.setInfragenericEpithet("Igen");
    n.setInfraspecificEpithet(null);
    n.setCandidatus(true);
    n.setCultivarEpithet("Red Rose");
    n.setStrain("ACTT 675213");
    n.setSourceUrl(URI.create("http://gbif.org"));
    n.setNotho(NamePart.SPECIFIC);
    n.setFossil(true);
    n.setRank(Rank.SPECIES);
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);
    n.updateScientificName();
    n.addRemark("my first note");
    n.addRemark("my second note");
    return n;
  }

  public static List<Name> newNames(int size) {
    List<Name> names = Lists.newArrayList();
    while (size-- > 0) {
      names.add(newName());
    }
    return names;
  }

  public static Synonymy newSynonymy() {
    Synonymy s = new Synonymy();
    s.addHeterotypicGroup(newNames(1 + RND.nextInt(3)));
    while (RND.nextBoolean() || RND.nextBoolean()) {
      s.addHeterotypicGroup(newNames(1 + RND.nextInt(6)));
    }
    return s;
  }

  public static Reference newReference() {
    return newReference(RandomUtils.randomString(25));
  }

  public static Reference newReference(String title) {
    Reference r = new Reference();
    r.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    CslData csl = new CslData();
    r.setCsl(csl);
    csl.setType(CSLRefType.ARTICLE_JOURNAL);
    csl.setTitle(title);
    csl.setContainerTitle("Nature");
    csl.setVolume("556");
    csl.setAbstrct("a very long article you should read");
    CslName author1 = new CslName();
    author1.setGiven("John");
    author1.setFamily("Smith");
    CslName author2 = new CslName();
    author2.setGiven("Betty");
    author2.setFamily("Jones");
    csl.setAuthor(new CslName[] {author1, author2});
    CslDate date = new CslDate();
    date.setDateParts(new int[][] {{2014, 8, 12}});
    csl.setAccessed(date);
    csl.setCategories(new String[] {"A", "B", "C"});
    return r;
  }

  public static Authorship createAuthorship() {
    Authorship a = new Authorship();
    while (a.getAuthors().size() < 2 || RND.nextBoolean()) {
      a.getAuthors().add(RandomUtils.randomAuthor());
    }
    a.setYear(RandomUtils.randomSpeciesYear());
    return a;
  }
  public static CslData createCsl() {
    CslData csl = (CslData) random.create(CslData.class, CslName.class, CslDate.class);
    csl.getOriginalDate().setDateParts(new int[][] {{1752, 4, 4}, {1752, 8, 4}});
    csl.getSubmitted().setDateParts(new int[][] {{1850, 6, 12}});
    return csl;
  }

  public static VerbatimRecord createVerbatim() {
    VerbatimRecord rec = new VerbatimRecord(11, "myFile.txt", DwcTerm.Taxon);
    rec.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    for (Term t : DwcTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + RND.nextInt(23)).toLowerCase());
    }
    for (Term t : DcTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + RND.nextInt(77)));
    }
    for (Term t : GbifTerm.values()) {
      rec.put(t, RandomUtils.randomString(1 + RND.nextInt(8)));
    }
    rec.put(UnknownTerm.build("http://col.plus/terms/punk"), RandomUtils.randomString(500 + RND.nextInt(2000)));
    rec.addIssue(Issue.ACCEPTED_NAME_MISSING);
    rec.addIssue(Issue.POTENTIAL_VARIANT);
    return rec;
  }

}
