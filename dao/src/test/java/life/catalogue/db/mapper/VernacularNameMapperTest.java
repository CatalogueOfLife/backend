package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.VernacularName;
import life.catalogue.api.search.VernacularNameUsage;
import life.catalogue.api.search.VernacularSearchRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.newVernacularName;
import static org.junit.Assert.assertEquals;

public class VernacularNameMapperTest extends TaxonExtensionMapperTest<VernacularName, VernacularNameMapper> {

	public VernacularNameMapperTest() {
		super(VernacularNameMapper.class);
	}
	
	@Override
	List<VernacularName> createTestEntities() {
    List<VernacularName> objs = new ArrayList<>();
		for (String l: new String[]{"eng", "deu", "fra"}) {
			VernacularName v = newVernacularName(RandomUtils.randomLatinString(30));
			v.setLanguage(l);
			objs.add(TestEntityGenerator.setUserDate(v));
		}
		// now sort by name as this is the order we expect in listByTaxon
		return objs.stream()
        .sorted(Comparator.comparing(VernacularName::getName))
        .collect(Collectors.toList());
	}

  @Test
  public void search() throws Exception {
    insert("Brauner Berg-Adler", "deu");
    insert("Grüner Berg Adler", "deu");
    insert("Seeadler", "deu");
    commit();
    VernacularSearchRequest req = VernacularSearchRequest.byQuery("adler");
	  List<VernacularNameUsage> resp = mapper().search(tax.getDatasetKey(), req, new Page());
	  assertEquals(2, resp.size());
    assertEquals(2, mapper().count(tax.getDatasetKey(), req));
  }

  @Test
  public void searchAll() throws Exception {
    insert("Brauner Berg-Adler", "deu");
    insert("Grüner Berg Adler", "deu");
    insert("Seeadler", "deu");
    commit();
    List<VernacularNameUsage> resp = mapper().searchAll("adler", null, new Page());
    assertEquals(2, resp.size());

    // work with spaces and quotes
    mapper().searchAll("grüner adler", null, new Page());
    mapper().searchAll("adler's", null, new Page());
  }

  VernacularName insert(String name, String lang) {
	  if (tax == null) {
      tax = TestEntityGenerator.newTaxon(3);
      insertTaxon(tax);
    }
    VernacularName v = newVernacularName(name);
    v.setDatasetKey(tax.getDatasetKey());
    v.setLanguage(lang);
    TestEntityGenerator.setUser(v);
    mapper().create(v, tax.getId());
    return v;
  }
}