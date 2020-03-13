package life.catalogue.db.mapper;

import java.util.ArrayList;
import java.util.List;

import life.catalogue.db.mapper.DescriptionMapper;
import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Description;
import life.catalogue.api.vocab.Gazetteer;

/**
 *
 */
public class DescriptionMapperTest extends TaxonExtensionMapperTest<Description, DescriptionMapper> {

	public DescriptionMapperTest() {
		super(DescriptionMapper.class);
	}
	
	@Override
	List<Description> createTestEntities() {
		List<Description> ds = new ArrayList<>();
		for (Gazetteer g : Gazetteer.values()) {
			for (String l: new String[]{"eng", "deu", "fra"}) {
				Description d = new Description();
				d.setCategory("Etymology");
				d.setDescription(RandomUtils.randomLatinString(1000));
				d.setLanguage(l);
				ds.add(TestEntityGenerator.setUserDate(d));
			}
		}
		return ds;
	}
}