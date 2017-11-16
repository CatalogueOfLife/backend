package org.col;

import com.google.common.base.Splitter;
import org.col.api.*;
import org.col.api.vocab.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * utility class to generate new test instances to be used in tests.
 */
public class TestEntityGenerator {

	public final static Random RND = new Random();
	private static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();

	/**
	 * Corresponds exactly to 1st dataset record inserted via squirrels.sql
	 */
	public final static Dataset DATASET1 = new Dataset();
	/**
	 * Corresponds exactly to 2nd dataset record inserted via squirrels.sql
	 */
	public final static Dataset DATASET2 = new Dataset();
	/**
	 * Corresponds exactly to 1st name record inserted via squirrels.sql
	 */
	public final static Name NAME1 = new Name();
	/**
	 * Corresponds exactly to 2nd name record inserted via squirrels.sql
	 */
	public final static Name NAME2 = new Name();
	/**
	 * Corresponds exactly to 1st taxon record inserted via squirrels.sql
	 */
	public final static Taxon TAXON1 = new Taxon();
	/**
	 * Corresponds exactly to 2nd taxon record inserted via squirrels.sql
	 */
	public final static Taxon TAXON2 = new Taxon();
	/**
	 * Corresponds exactly to 1st reference record inserted via squirrels.sql
	 */
	public final static Reference REF1 = new Reference();
	/**
	 * Corresponds exactly to 2nd reference record inserted via squirrels.sql
	 */
	public final static Reference REF2 = new Reference();

	static {
		DATASET1.setKey(1);
		DATASET2.setKey(2);

		NAME1.setKey(1);
		NAME1.setId("name-1");
		NAME1.setDatasetKey(DATASET1.getKey());
		NAME1.setScientificName("Malus sylvestris");

		NAME2.setKey(2);
		NAME2.setId("name-2");
		NAME2.setDatasetKey(DATASET1.getKey());
		NAME2.setScientificName("Larus fuscus");

		TAXON1.setKey(1);
		TAXON1.setId("root-1");
		TAXON1.setDatasetKey(DATASET1.getKey());
		TAXON1.setName(NAME1);

		TAXON2.setKey(2);
		TAXON2.setId("root-2");
		TAXON2.setDatasetKey(DATASET1.getKey());
		TAXON2.setName(NAME2);

		REF1.setKey(1);
		REF1.setId("ref-1");
		REF1.setDatasetKey(DATASET1.getKey());

		REF2.setKey(2);
		REF2.setId("ref-2");
		REF2.setDatasetKey(DATASET2.getKey());
	}

	/*
	 * Creates a VernacularName using the specified vernacular name, belonging to
	 * the specified taxon and dataset DATASET1.
	 */
	public static VernacularName newVernacularName(String name) throws Exception {
		VernacularName vn = new VernacularName();
		vn.setName(name);
		vn.setLanguage(Language.ENGLISH);
		vn.setCountry(Country.UNITED_KINGDOM);
		return vn;
	}

	/*
	 * Creates a new taxon with the specified id, belonging to dataset DATASET1.
	 */
	public static Taxon newTaxon(String id) throws Exception {
		return newTaxon(DATASET1, id);
	}

	/*
	 * Creates a new taxon with the specified id, belonging to the specified
	 * dataset.
	 */
	public static Taxon newTaxon(Dataset dataset, String id) throws Exception {
		Taxon t = new Taxon();
		t.setAccordingTo("Foo");
		t.setAccordingToDate(LocalDate.of(2010, 11, 24));
		t.setDatasetKey(dataset.getKey());
		t.setDatasetUrl(URI.create("http://foo.com"));
		t.setFossil(true);
		t.setId(id);
		t.setLifezones(EnumSet.of(Lifezone.BRACKISH, Lifezone.FRESHWATER, Lifezone.TERRESTRIAL));
		t.setName(NAME1);
		t.setOrigin(Origin.SOURCE);
		t.setParentKey(TAXON1.getKey());
		t.setStatus(TaxonomicStatus.ACCEPTED);
		t.setRank(Rank.CLASS);
		t.setRecent(true);
		t.setRemarks("Foo == Bar");
		t.setSpeciesEstimate(81);
		t.setSpeciesEstimateReferenceKey(REF1.getKey());
		t.addIssue(Issue.ACCEPTED_NAME_MISSING);
		t.addIssue(Issue.HOMONYM, "Abies alba");
		return t;
	}

	/*
	 * Creates a new name with the specified id, belonging to the specified dataset.
	 */
	public static Name newName(String id) throws Exception {
		Name n = newName();
		n.setId(id);
		return n;
	}

	public static Name newName() throws Exception {
		Name n = new Name();
		n.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
		n.setScientificName(RandomUtils.randomSpecies());
		n.setAuthorship(createAuthorship());
		List<String> tokens = SPACE_SPLITTER.splitToList(n.getScientificName());
		n.setGenus(tokens.get(0));
		n.setSpecificEpithet(tokens.get(1));
		n.setInfragenericEpithet("Igen");
		n.setInfraspecificEpithet(null);
		n.setNotho(NamePart.SPECIFIC);
		n.setFossil(true);
		n.setRank(Rank.SPECIES);
		n.setOrigin(Origin.SOURCE);
		n.setType(NameType.SCIENTIFIC);
		n.setEtymology("A random species name");
		n.addIssue(Issue.ACCEPTED_NAME_MISSING);
		n.addIssue(Issue.HOMONYM, "Abies alba");
		return n;
	}

	public static Authorship createAuthorship() throws Exception {
		Authorship a = new Authorship();
		while (a.getCombinationAuthors().size() < 2 || RND.nextBoolean()) {
			a.getCombinationAuthors().add(RandomUtils.randomAuthor());
		}
		a.setCombinationYear(RandomUtils.randomSpeciesYear());
		while (a.getBasionymAuthors().isEmpty() || RND.nextBoolean()) {
			a.getBasionymAuthors().add(RandomUtils.randomAuthor());
		}
		a.setBasionymYear(RandomUtils.randomSpeciesYear());
		return a;
	}
}
