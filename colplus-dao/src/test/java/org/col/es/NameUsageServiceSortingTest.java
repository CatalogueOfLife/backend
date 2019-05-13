package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.col.api.TestEntityGenerator;
import org.col.api.model.BareName;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.Rank;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;

public class NameUsageServiceSortingTest extends EsReadTestBase {

	private static RestClient client;
	private static NameUsageSearchService svc;

	@BeforeClass
	public static void init() {
		client = esSetupRule.getEsClient();
		svc = new NameUsageSearchService(indexName, esSetupRule.getEsClient());
	}

	@AfterClass
	public static void shutdown() throws IOException {
		// EsUtil.deleteIndex(client, indexName);
		client.close();
	}

	@Before
	public void before() throws IOException {
		EsUtil.deleteIndex(client, indexName);
		EsUtil.createIndex(client, indexName, getEsConfig().nameUsage);
	}

	@Test
	public void testSort1() throws IOException {
		NameUsageTransfer transfer = new NameUsageTransfer();
		EsNameUsage enu = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
		insert(client, indexName, enu);
		enu = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
		insert(client, indexName, enu);
		enu = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
		insert(client, indexName, enu);
		refreshIndex(client, indexName);
		assertEquals(3, EsUtil.count(client, indexName));
		NameSearchRequest nsr = new NameSearchRequest();
		nsr.setHighlight(false);
		// Force sorting by index order
		nsr.setSortBy(null);
		ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());
		assertEquals(3, result.getResult().size());
		assertEquals(Taxon.class, result.getResult().get(0).getUsage().getClass());
		assertEquals(Synonym.class, result.getResult().get(1).getUsage().getClass());
		assertEquals(BareName.class, result.getResult().get(2).getUsage().getClass());
	}

	@Test
	public void testSort2() throws IOException {
		NameUsageTransfer transfer = new NameUsageTransfer();
		EsNameUsage enu = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
		// Overwrite to test ordering by scientific name
		enu.setScientificNameWN("B");
		insert(client, indexName, enu);
		enu = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
		enu.setScientificNameWN("C");
		insert(client, indexName, enu);
		enu = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
		enu.setScientificNameWN("A");
		insert(client, indexName, enu);
		refreshIndex(client, indexName);
		assertEquals(3, EsUtil.count(client, indexName));
		NameSearchRequest nsr = new NameSearchRequest();
		nsr.setHighlight(false);
		nsr.setSortBy(SortBy.NAME);
		ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());
		assertEquals(3, result.getResult().size());
		assertEquals(BareName.class, result.getResult().get(0).getUsage().getClass());
		assertEquals(Taxon.class, result.getResult().get(1).getUsage().getClass());
		assertEquals(Synonym.class, result.getResult().get(2).getUsage().getClass());
	}

	@Test
	public void testSortReverse1() throws IOException {
		NameUsageTransfer transfer = new NameUsageTransfer();
		EsNameUsage enu = transfer.toDocument(TestEntityGenerator.newNameUsageTaxonWrapper());
		// Overwrite to test ordering by scientific name
		enu.setScientificNameWN("B");
		insert(client, indexName, enu);
		enu = transfer.toDocument(TestEntityGenerator.newNameUsageSynonymWrapper());
		enu.setScientificNameWN("C");
		insert(client, indexName, enu);
		enu = transfer.toDocument(TestEntityGenerator.newNameUsageBareNameWrapper());
		enu.setScientificNameWN("A");
		insert(client, indexName, enu);
		refreshIndex(client, indexName);
		assertEquals(3, EsUtil.count(client, indexName));
		NameSearchRequest nsr = new NameSearchRequest();
		nsr.setHighlight(false);
		nsr.setSortBy(SortBy.NAME);
		nsr.setReverse(true);
		ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());
		assertEquals(3, result.getResult().size());
		assertEquals(Synonym.class, result.getResult().get(0).getUsage().getClass());
		assertEquals(Taxon.class, result.getResult().get(1).getUsage().getClass());
		assertEquals(BareName.class, result.getResult().get(2).getUsage().getClass());
	}

	@Test
	public void testSortDescending() throws IOException {
		NameUsageTransfer transfer = new NameUsageTransfer();

		EsSearchRequest esr = EsSearchRequest.emptyRequest();
		esr.setSort(Arrays.asList(new SortField("scientificNameWN", false), new SortField("rank", false)));

		// Create name usage in the order we expect them to come out, then shuffle.
		Name n = new Name();
		n.setScientificName("C");
		n.setRank(Rank.SPECIES);
		Taxon t = new Taxon();
		t.setName(n);
		final NameUsageWrapper nuw1 = new NameUsageWrapper(t);

		n = new Name();
		n.setScientificName("B");
		n.setRank(Rank.GENUS);
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw2 = new NameUsageWrapper(t);

		n = new Name();
		n.setScientificName("B");
		n.setRank(Rank.PHYLUM);
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw3 = new NameUsageWrapper(t);

		n = new Name();
		n.setScientificName("A");
		n.setRank(Rank.INFRASPECIFIC_NAME);
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw4 = new NameUsageWrapper(t);

		List<NameUsageWrapper> all = Arrays.asList(nuw1, nuw2, nuw3, nuw4);

		List<NameUsageWrapper> shuffled = new ArrayList<>(all);
		Collections.shuffle(shuffled);
		shuffled.stream().map(t1 -> {
			try {
				return transfer.toDocument(t1);
			} catch (IOException e) {
				throw new RuntimeException();
			}
		}).forEach(x -> {
			try {
				insert(client, indexName, x);
			} catch (IOException e) {
				throw new EsException(e);
			}
		});
		refreshIndex(client, indexName);

		ResultPage<NameUsageWrapper> result = svc.search(indexName, esr, new Page());

		assertEquals(all, result.getResult());
	}

	@Test
	public void testSortTaxonomic() throws IOException {
		NameUsageTransfer transfer = new NameUsageTransfer();

		// Define search
		NameSearchRequest nsr = new NameSearchRequest();
		nsr.setHighlight(false);
		nsr.setSortBy(SortBy.TAXONOMIC);

		// Don't forget this one; we're going to insert more than 10 docs
		Page page = new Page(100);

		// Create name usage in the order we expect them to come out, then shuffle.
		Name n = new Name();
		n.setRank(Rank.KINGDOM);
		n.setScientificName("B");
		Taxon t = new Taxon();
		t.setName(n);
		final NameUsageWrapper nuw1 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.KINGDOM);
		n.setScientificName("Z");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw2 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.PHYLUM);
		n.setScientificName("C");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw3 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.PHYLUM);
		n.setScientificName("E");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw4 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.CLASS);
		n.setScientificName("Y");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw5 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.ORDER);
		n.setScientificName("X");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw6 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.FAMILY);
		n.setScientificName("V");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw7 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.GENUS);
		n.setScientificName("Q");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw8 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.SPECIES);
		n.setScientificName("K");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw9 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.SPECIES);
		n.setScientificName("L");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw10 = new NameUsageWrapper(t);

		n = new Name();
		n.setRank(Rank.SPECIES);
		n.setScientificName("M");
		t = new Taxon();
		t.setName(n);
		NameUsageWrapper nuw11 = new NameUsageWrapper(t);

		List<NameUsageWrapper> all = Arrays.asList(nuw1, nuw2, nuw3, nuw4, nuw5, nuw6, nuw7, nuw8, nuw9, nuw10, nuw11);

		List<NameUsageWrapper> shuffled = new ArrayList<>(all);
		Collections.shuffle(shuffled);
		shuffled.stream().map(t1 -> {
			try {
				return transfer.toDocument(t1);
			} catch (IOException e) {
				throw new RuntimeException();
			}
		}).forEach(x -> {
			try {
				insert(client, indexName, x);
			} catch (IOException e) {
				throw new EsException(e);
			}
		});
		refreshIndex(client, indexName);

		ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, page);

		assertEquals(all, result.getResult());

	}

}
