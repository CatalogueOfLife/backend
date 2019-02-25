package org.col.db.mapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Language;

import static org.col.api.TestEntityGenerator.newVernacularName;
import static org.col.api.TestEntityGenerator.setUserDate;

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
		for (Language l : Language.values()) {
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