package org.col.dao;

import org.col.api.*;
import org.col.api.vocab.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Random;

public class DaoTestUtil {

	public final static Random RND = new Random();
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
		NAME1.setDataset(DATASET1);
		NAME1.setScientificName("Malus sylvestris");

		NAME2.setKey(2);
		NAME2.setId("name-2");
		NAME2.setDataset(DATASET1);
		NAME2.setScientificName("Larus fuscus");

		TAXON1.setKey(1);
		TAXON1.setId("root-1");
		TAXON1.setDataset(DATASET1);
		TAXON1.setName(NAME1);

		TAXON2.setKey(2);
		TAXON2.setId("root-2");
		TAXON2.setDataset(DATASET1);
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
	 * taxon TAXON1 and dataset DATASET1.
	 */
	public static VernacularName newVernacularName(String name) throws Exception {
		return newVernacularName(TAXON1, name);
	}

	/*
	 * Creates a VernacularName using the specified vernacular name, belonging to
	 * the specified taxon and dataset DATASET1.
	 */
	public static VernacularName newVernacularName(Taxon taxon, String name) throws Exception {
		VernacularName vn = new VernacularName();
		vn.setDatasetKey(DATASET1.getKey());
		vn.setTaxonKey(taxon.getKey());
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
		t.setDataset(dataset);
		t.setDatasetUrl(URI.create("http://foo.com"));
		t.setFossil(true);
		t.setId(id);
		t.setLifezones(EnumSet.of(Lifezone.BRACKISH, Lifezone.FRESHWATER, Lifezone.TERRESTRIAL));
		t.setName(NAME1);
		t.setOrigin(Origin.SOURCE);
		t.setParent(TAXON1);
		t.setStatus(TaxonomicStatus.ACCEPTED);
		t.setRank(Rank.CLASS);
		t.setRecent(true);
		t.setRemarks("Foo == Bar");
		t.setSpeciesEstimate(81);
		t.setSpeciesEstimateReference(REF1);
		t.addIssue(Issue.ACCEPTED_NAME_MISSING);
		t.addIssue(Issue.HOMONYM, "Abies alba");
		return t;
	}
}
