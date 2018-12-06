package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;

import org.col.api.RandomUtils;
import org.col.api.model.Description;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.Language;

import static org.col.api.TestEntityGenerator.setUserManaged;

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
			for (Language l: Language.values()) {
				Description d = new Description();
				d.setCategory("Etymology");
				d.setDescription(RandomUtils.randomString(1000));
				d.setLanguage(l);
				ds.add(setUserManaged(d));
			}
		}
		return ds;
	}
}