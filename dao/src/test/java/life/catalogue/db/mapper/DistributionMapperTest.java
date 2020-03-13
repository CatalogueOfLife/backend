package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Distribution;
import life.catalogue.api.vocab.DistributionStatus;
import life.catalogue.api.vocab.Gazetteer;

import java.util.ArrayList;
import java.util.List;

import static life.catalogue.api.TestEntityGenerator.setUserDate;

/**
 *
 */
public class DistributionMapperTest extends TaxonExtensionMapperTest<Distribution, DistributionMapper> {

	public DistributionMapperTest() {
		super(DistributionMapper.class);
	}
	
	@Override
	List<Distribution> createTestEntities() {
		List<Distribution> ds = new ArrayList<>();
		for (Gazetteer g : Gazetteer.values()) {
			for (DistributionStatus status : DistributionStatus.values()) {
				Distribution d = new Distribution();
				d.setArea("Europe");
				d.setGazetteer(g);
				d.setStatus(status);
				ds.add(TestEntityGenerator.setUserDate(d));
			}
		}
		return ds;
	}
	
}