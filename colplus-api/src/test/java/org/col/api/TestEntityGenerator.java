package org.col.api;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * utility class to metrics new test instances to be used in tests.
 */
public class TestEntityGenerator {

  public final static Random RND = new Random();
  private static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();

  /**
   * Corresponds exactly to 1st dataset record inserted via apple.sql
   */
  public final static Dataset DATASET1 = new Dataset();
  /**
   * Corresponds exactly to 2nd dataset record inserted via apple.sql
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
   * Corresponds exactly to 1st taxon record inserted via apple.sql
   */
  public final static Taxon TAXON1 = new Taxon();
  /**
   * Corresponds exactly to 2nd taxon record inserted via apple.sql
   */
  public final static Taxon TAXON2 = new Taxon();
  /**
   * Corresponds exactly to 1st reference record inserted via apple.sql
   */
  public final static Reference REF1 = new Reference();
  /**
   * Corresponds exactly to 2nd reference record inserted via apple.sql
   */
  public final static Reference REF2 = new Reference();

  static {
    DATASET1.setKey(1);
    DATASET2.setKey(2);

    REF1.setKey(1);
    REF1.setId("ref-1");
    REF1.setDatasetKey(DATASET1.getKey());

    REF2.setKey(2);
    REF2.setId("ref-2");
    REF2.setDatasetKey(DATASET2.getKey());

    NAME1.setKey(1);
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
<<<<<<< HEAD
	 * Creates a new taxon with the specified id, belonging to the specified
	 * dataset.
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
		t.addIssue(Issue.ACCEPTED_NAME_MISSING);
		t.addIssue(Issue.HOMONYM);
		return t;
	}

  /*
	 * Creates a new taxon with the specified id, belonging to the specified
	 * dataset.
	 */
  public static Synonym newMisapplied(Name name, int... acceptedKeys) {
    Synonym s = new Synonym();
    s.setName(name);
    s.setAccordingTo("non Döring 1999");
    s.setStatus(TaxonomicStatus.MISAPPLIED);
    for (int acc : acceptedKeys) {
      Taxon t = new Taxon();
      t.setKey(acc);
      t.setDatasetKey(name.getDatasetKey());
      s.getAccepted().add(t);
    }
    return s;
  }

  public static Synonym newSynonym() {
    Synonym s = new Synonym();
    s.setName(newName());
    s.setAccordingTo("auct. amer.");
    s.setStatus(TaxonomicStatus.SYNONYM);

    Taxon t = newTaxon(s.getName().getDatasetKey(), RandomUtils.randomString(25));
    t.setName(newName());
    s.getAccepted().add(t);
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
    n.addIssue(Issue.ACCEPTED_NAME_MISSING);
    n.addIssue(Issue.HOMONYM);
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
    s.addHomotypicGroup(newNames(1+RND.nextInt(3)));
    while (RND.nextBoolean() || RND.nextBoolean()) {
      s.addHomotypicGroup(newNames(1+RND.nextInt(6)));
    }
    return s;
  }

  public static Reference newReference() {
    return newReference(RandomUtils.randomString(25));
  }

  public static Reference newReference(String title) {
    Reference r = Reference.create();
    r.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    r.getCsl().setTitle(title);
    CslName author1 = new CslName();
    author1.setGiven("John");
    author1.setFamily("Smith");
    CslName author2 = new CslName();
    author2.setGiven("Betty");
    author2.setFamily("Jones");
    r.getCsl().setAuthor(new CslName[] {author1, author2});
    CslDate date = new CslDate();
    date.setDateParts(new int[][] {{2014, 8, 12}});
    r.getCsl().setAccessed(date);
    r.getCsl().setCategories(new String[] {"A", "B", "C"});
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
}
