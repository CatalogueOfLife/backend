package org.col.db.mapper;

import org.col.api.Distribution;
import org.col.api.vocab.DistributionStatus;
import org.junit.Test;

import java.util.List;

import static org.col.TestEntityGenerator.DATASET1;
import static org.col.TestEntityGenerator.TAXON1;
import static org.junit.Assert.*;

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
      Distribution out = mapper().getByKey(in.getKey());
      assertTrue(in.equalsShallow(out));
    }
	}

  @Test
	public void testGetDistributions() throws Exception {
		Distribution a = newDistribution("a");
		mapper().create(a);
		Distribution b = newDistribution("b");
		mapper().create(b);
		Distribution c = newDistribution("c");
		mapper().create(c);
		List<Distribution> list = mapper().getDistributions(DATASET1.getKey(), TAXON1.getId());
		assertEquals("01",3, list.size());
		assertTrue("02",a.equalsShallow(list.get(0)));
		assertTrue("03",b.equalsShallow(list.get(1)));
		assertTrue("04",c.equalsShallow(list.get(2)));
	}

	private static Distribution newDistribution(String area) {
		Distribution d = new Distribution();
		d.setDataset(DATASET1);
		d.setTaxon(TAXON1);
		d.setArea(area);
		d.setAreaStandard(7);
		d.setStatus(DistributionStatus.NATIVE);
		return d;
	}
}