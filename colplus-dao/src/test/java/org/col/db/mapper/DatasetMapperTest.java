package org.col.db.mapper;

import com.google.common.collect.Lists;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.RandomUtils;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.License;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 *
 */
public class DatasetMapperTest extends MapperTestBase<DatasetMapper> {

	public DatasetMapperTest() {
		super(DatasetMapper.class);
	}

	private static Dataset create() throws Exception {
		Dataset d = new Dataset();
		d.setGbifKey(UUID.randomUUID());
		d.setTitle(RandomUtils.randomString(80));
		d.setDescription(RandomUtils.randomString(500));
		d.setLicense(License.CC0);
		for (int i = 0; i < 8; i++) {
			d.getAuthorsAndEditors().add(RandomUtils.randomString(100));
		}
		d.setContactPerson("Hans Peter");
		d.setDataAccess(URI.create("https://api.gbif.org/v1/dataset/" + d.getGbifKey()));
		d.setDataFormat(DataFormat.SQL);
		d.setReleaseDate(LocalDate.now());
		d.setVersion("v123");
		d.setHomepage(URI.create("https://www.gbif.org/dataset/" + d.getGbifKey()));
		d.setNotes("my notes");
		d.setOrganisation("my org");
		return d;
	}

	@Test
	public void roundtrip() throws Exception {
		Dataset d1 = create();
		mapper().create(d1);

		commit();

		Dataset d2 = mapper().get(d1.getKey());
		// remove newly set property
		d2.setCreated(null);
    d2.setModified(null);

		assertEquals(d1, d2);
	}

	@Test
	public void delete() throws Exception {
		Dataset d1 = create();
		mapper().create(d1);

		commit();

		// not deleted yet
		Dataset d = mapper().get(d1.getKey());
		assertNull(d.getDeleted());
		assertNotNull(d.getCreated());

		// mark deleted
		mapper().delete(d1.getKey());
		d = mapper().get(d1.getKey());
		assertNotNull(d.getDeleted());
	}

	@Test
	public void count() throws Exception {
		assertEquals(2, mapper().count(null));

		mapper().create(create());
		mapper().create(create());
		// even thogh not committed we are in the same session so we see the new
		// datasets already
		assertEquals(4, mapper().count(null));

		commit();
		assertEquals(4, mapper().count(null));
	}

	@Test
	public void list() throws Exception {
		List<Dataset> ds = Lists.newArrayList();
		ds.add(mapper().get(1));
		ds.add(mapper().get(2));
		ds.add(create());
		ds.add(create());
		ds.add(create());
		ds.add(create());
		ds.add(create());

		for (Dataset d : ds) {
			if (d.getKey() == null) {
				mapper().create(d);
			}
			// dont compare created stamps
			d.setCreated(null);
		}
		commit();
    removeCreated(ds);

		// get first page
		Page p = new Page(0, 4);


    List<Dataset> res = removeCreated(mapper().list(p));
		assertEquals(4, res.size());
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(ds.get(0), res.get(0));
    assertEquals(0, diff.getChanges().size());
    assertEquals(ds.subList(0,4), res);

		// next page
		p.next();
		res = removeCreated(mapper().list(p));
		assertEquals(3, res.size());
		assertEquals(ds.subList(4,7), res);
	}

  @Test
  public void listNeverImported() throws Exception {
    List<Dataset> ds = Lists.newArrayList();
    ds.add(mapper().get(1));
    ds.add(mapper().get(2));
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());

    for (Dataset d : ds) {
      if (d.getKey() == null) {
        mapper().create(d);
      }
    }
    commit();

    List<Dataset> never = mapper().listNeverImported(3);
    assertEquals(3, never.size());

    List<Dataset> tobe = mapper().listToBeImported(3);
    assertEquals(0, tobe.size());
  }

  @Test
	public void countSearchResults() throws Exception {
		createSearchableDataset("BIZ", "CUIT", "A sentence with worms and stuff");
		createSearchableDataset("ITIS", "ITIS", "Also contains worms");
		createSearchableDataset("WORMS", "WORMS", "The Worms dataset");
		createSearchableDataset("FOO", "BAR", null);
		commit();
		int count = mapper().count("worms");
		assertEquals("01", 3, count);
	}

	@Test
	public void search() throws Exception {
		createSearchableDataset("BIZ", "CUIT", "A sentence with worms and stuff");
		createSearchableDataset("ITIS", "ITIS", "Also contains worms");
		createSearchableDataset("WORMS", "WORMS", "The Worms dataset");
		createSearchableDataset("FOO", "BAR", null);
		commit();
		List<Dataset> datasets = mapper().search("worms", new Page());
		assertEquals("01", 3, datasets.size());
		// check order by rank:
		assertEquals("02", "WORMS", datasets.get(0).getTitle());
		datasets.forEach(c -> Assert.assertNotEquals("03", "FOO", c.getTitle()));
	}

	private static List<Dataset> removeCreated(List<Dataset> ds) {
		for (Dataset d : ds) {
			// dont compare created stamps
			d.setCreated(null);
      d.setModified(null);
		}
		return ds;
	}

	private void createSearchableDataset(String title, String organisation, String description) {
		Dataset ds = new Dataset();
		ds.setTitle(title);
		ds.setOrganisation(organisation);
		ds.setDescription(description);
		mapper().create(ds);
	}
}