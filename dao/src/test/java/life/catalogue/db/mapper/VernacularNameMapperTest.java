package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.VernacularName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static life.catalogue.api.TestEntityGenerator.newVernacularName;

/**
 *
 */
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

}