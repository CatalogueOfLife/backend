package org.col.db.mapper;

import static org.col.TestEntityGenerator.DATASET1;
import static org.col.TestEntityGenerator.TAXON1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.col.api.Distribution;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;
import org.junit.Test;

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
			mapper().create(in);
			assertNotNull(in.getKey());
			commit();
			Distribution out = mapper().get(in.getKey());
			assertEquals("01", in, out);
		}
	}

	private static Distribution newDistribution(String area) {
		Distribution d = new Distribution();
		d.setDatasetKey(DATASET1.getKey());
		d.setTaxonKey(TAXON1.getKey());
		d.setArea(area);
		d.setAreaStandard(Gazetteer.TDWG);
		d.setStatus(DistributionStatus.NATIVE);
		return d;
	}
}