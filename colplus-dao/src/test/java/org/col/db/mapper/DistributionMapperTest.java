package org.col.db.mapper;

import org.col.api.model.Distribution;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;

import java.util.ArrayList;
import java.util.List;

import static org.col.api.TestEntityGenerator.setUserManaged;

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
				ds.add(setUserManaged(d));
			}
		}
		return ds;
	}
	
}