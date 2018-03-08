package org.col.db.mapper;

import org.col.api.model.Distribution;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;
import org.col.api.TestEntityGenerator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class DistributionMapperTest extends MapperTestBase<DistributionMapper> {

	public DistributionMapperTest() {
		super(DistributionMapper.class);
	}

	@Test
	public void roundtrip() throws Exception {
		Distribution in = newDistribution("Europe");
		for (DistributionStatus status : DistributionStatus.values()) {
			in.setStatus(status);
			mapper().create(in, TestEntityGenerator.TAXON1.getKey(), TestEntityGenerator.DATASET1.getKey());
			assertNotNull(in.getKey());
			commit();
			Distribution out = mapper().get(in.getKey());
			assertEquals("01", in, out);
		}
	}

	private static Distribution newDistribution(String area) {
		Distribution d = new Distribution();
		d.setArea(area);
		d.setGazetteer(Gazetteer.TDWG);
		d.setStatus(DistributionStatus.NATIVE);
		return d;
	}
}