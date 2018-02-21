package org.col.admin.task.importer;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import jersey.repackaged.com.google.common.base.Throwables;
import jersey.repackaged.com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.admin.config.ImporterConfig;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.NeoDbFactory;
import org.col.admin.task.importer.neo.model.RankedName;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.db.dao.NameDao;
import org.col.db.dao.ReferenceDao;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.*;
import org.gbif.nameparser.api.Rank;
import org.junit.*;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.col.api.vocab.DataFormat.ACEF;
import static org.col.api.vocab.DataFormat.DWCA;
import static org.junit.Assert.*;

/**
 *
 */
public class PgImportIT {
	private NeoDb store;
	private NormalizerConfig cfg;
	private ImporterConfig icfg = new ImporterConfig();
	private Dataset dataset;

	@ClassRule
	public static PgSetupRule pgSetupRule = new PgSetupRule();

	@Rule
	public InitMybatisRule initMybatisRule = InitMybatisRule.empty();

	@Before
	public void initCfg() throws Exception {
		cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
		dataset = new Dataset();
	}

	@After
	public void cleanup() throws Exception {
		if (store != null) {
			// store is close by Normalizer.run method already
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
		}
	}

  void normalizeAndImport(DataFormat format, int key) throws Exception {
    URL url = getClass().getResource("/" + format.name().toLowerCase() + "/" + key);
    dataset.setDataFormat(format);
    normalizeAndImport(Paths.get(url.toURI()));
  }

	void normalizeAndImport(Path source) {
    try {
      // insert dataset
      dataset.setTitle("Test Dataset " + source.toString());

      SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
      // this creates a new datasetKey, usually 1!
      session.getMapper(DatasetMapper.class).create(dataset);
      session.commit();
      session.close();

      // normalize
      Normalizer norm = new Normalizer(NeoDbFactory.create(cfg, dataset.getKey()), source.toFile(), dataset.getDataFormat());
      norm.run();

      // import into postgres
      store = NeoDbFactory.open(cfg, dataset.getKey());
      PgImport importer = new PgImport(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(),
          icfg);
      importer.run();

    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  void normalizeAndImport(URI url, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // download an decompress
    ExternalSourceUtil.consumeSource(url, this::normalizeAndImport);
  }

  @Test
  public void testPublishedIn() throws Exception {
    normalizeAndImport(DWCA, 0);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameDao ndao = new NameDao(session);
      NameActMapper actMapper = session.getMapper(NameActMapper.class);
      ReferenceMapper rMapper= session.getMapper(ReferenceMapper.class);
      ReferenceDao rdao = new ReferenceDao(session);

      Name trametes_modesta = ndao.get(ndao.lookupKey("324805", dataset.getKey()));

      List<NameAct> acts = actMapper.listByName(trametes_modesta.getKey());
      assertEquals(1, acts.size());
      NameAct act = acts.get(0);
      assertNotNull(act.getReferenceKey());

      Reference pubIn = rdao.get(act.getReferenceKey());
      ReferenceWithPage pubIn2 = rMapper.getPublishedIn(trametes_modesta.getKey());
      assertEquals(pubIn, pubIn2.getReference());
      assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getTitle());
      assertNotNull(pubIn.getKey());
      assertNull(pubIn.getId());
    }
  }

  @Test
	public void testDwca1() throws Exception {
		normalizeAndImport(DWCA, 1);

		// verify results
		try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
			TaxonDao tdao = new TaxonDao(session);
			NameDao ndao = new NameDao(session);

			// check basionym
			Name n1006 = ndao.get(ndao.lookupKey("1006", dataset.getKey()));
			assertEquals("Leontodon taraxacoides", n1006.getScientificName());

			Name bas = ndao.get(n1006.getBasionymKey());
			assertEquals("Leonida taraxacoida", bas.getScientificName());
			assertEquals("1006-s3", bas.getId());

			// check taxon parents
			assertParents(tdao, "1006", "102", "30", "20", "10", "1");

			// TODO: check synonym
		}
	}

	@Test
	public void testSupplementary() throws Exception {
		normalizeAndImport(DWCA, 24);

		// verify results
		try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
			TaxonDao tdao = new TaxonDao(session);

			// check species name
			Taxon tax = tdao.get(tdao.lookupKey("1000",dataset.getKey()));
			assertEquals("Crepis pulchra", tax.getName().getScientificName());

			TaxonInfo info = tdao.getTaxonInfo(tax.getKey());
			// check vernaculars
			Map<Language, String> expV = Maps.newHashMap();
			expV.put(Language.GERMAN, "Schöner Pippau");
			expV.put(Language.ENGLISH, "smallflower hawksbeard");
			assertEquals(expV.size(), info.getVernacularNames().size());
			for (VernacularName vn : info.getVernacularNames()) {
				assertEquals(expV.remove(vn.getLanguage()), vn.getName());
			}
			assertTrue(expV.isEmpty());

			// check distributions
			Set<Distribution> expD = Sets.newHashSet();
			expD.add(dist(Gazetteer.TEXT, "All of Austria and the alps", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "DE", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "FR", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "DK", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "GB", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "NG", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "KE", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "AGS", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.FAO, "37.4.1", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "MOR-MO", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "MOR-CE", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "MOR-ME", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "CPP", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "NAM", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "IT-82", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "ES-CN", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "FR-H", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "FM-PNI", DistributionStatus.NATIVE));

			assertEquals(expD.size(), info.getDistributions().size());
			// remove dist keys before we check equality
			info.getDistributions().forEach(d -> d.setKey(null));
			Set<Distribution> imported = Sets.newHashSet(info.getDistributions());

			Sets.SetView<Distribution> diff = Sets.difference(expD, imported);
			for (Distribution d : diff) {
				System.out.println(d);
			}
			assertEquals(expD, imported);
		}
	}

  @Test
  public void testAcef() throws Exception {
    normalizeAndImport(ACEF, 69);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameDao ndao = new NameDao(session);
      TaxonDao tdao = new TaxonDao(session);

      Taxon t = tdao.get("Rho-144", dataset.getKey());
      assertEquals("Afrogamasellus lokelei Daele, 1976", t.getName().canonicalNameComplete());

      List<Taxon> classific = tdao.getClassification(t.getKey());
      LinkedList<RankedName> expected = Lists.newLinkedList( Lists.newArrayList(
          rn(Rank.KINGDOM, "Animalia"),
          rn(Rank.PHYLUM, "Arthropoda"),
          rn(Rank.CLASS, "Arachnida"),
          rn(Rank.ORDER, "Mesostigmata"),
          rn(Rank.SUPERFAMILY, "Rhodacaroidea"),
          rn(Rank.FAMILY, "Rhodacaridae"),
          rn(Rank.GENUS, "Afrogamasellus")
      ));

      assertEquals(expected.size(), classific.size());
      for (Taxon ht : classific) {
        RankedName expect = expected.removeLast();
        assertEquals(expect.rank, ht.getName().getRank());
        assertEquals(expect.name, ht.getName().canonicalNameComplete());
      }

      assertEquals(TaxonomicStatus.ACCEPTED, t.getStatus());
      assertEquals("Tester", t.getAccordingTo());
      assertEquals("2008-01-01", t.getAccordingToDate().toString());
      assertFalse(t.isFossil());
      assertTrue(t.isRecent());
      assertTrue(t.getLifezones().isEmpty());
      assertNull(t.getRemarks());
      assertNull(t.getDatasetUrl());
      assertNull(t.getTaxonID());


      // test synonym
      Name syn = ndao.get("Rho-140", dataset.getKey());
      assertEquals("Rhodacarus guevarai Guevara-Benitez, 1974", syn.canonicalNameComplete());

      List<Taxon> acc = tdao.getAccepted(syn);
      assertEquals(1, acc.size());

      t = tdao.get("Rho-61", dataset.getKey());
      assertEquals("Multidentorhodacarus denticulatus (Berlese, 1920)", t.getName().canonicalNameComplete());
      assertEquals(t, acc.get(0));
    }
  }

  @Test
  @Ignore
  public void testGsdGithub() throws Exception {
    normalizeAndImport(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/assembly/73.tar.gz"), DataFormat.ACEF);
  }

  private static RankedName rn(Rank rank, String name) {
	  return new RankedName(null, name, null, rank);
  }

	private Distribution dist(Gazetteer standard, String area, DistributionStatus status) {
		Distribution d = new Distribution();
		d.setArea(area);
		d.setAreaStandard(standard);
		d.setStatus(status);
		return d;
	}

	private void assertParents(TaxonDao tdao, String taxonID, String... parentIds) {
		final LinkedList<String> expected = new LinkedList<String>(Arrays.asList(parentIds));
		Taxon t = tdao.get(tdao.lookupKey(taxonID,dataset.getKey()));
		while (t.getParentKey() != null) {
			Taxon parent = tdao.get(t.getParentKey());
			assertEquals(expected.pop(), parent.getId());
			t = parent;
		}
		assertTrue(expected.isEmpty());
	}

}