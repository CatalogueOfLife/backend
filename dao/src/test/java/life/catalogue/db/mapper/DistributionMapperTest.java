package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Distribution;
import life.catalogue.api.vocab.*;

import java.util.ArrayList;
import java.util.List;

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
			for (var em : EstablishmentMeans.values()) {
				Distribution d = new Distribution();
				Area area = new AreaImpl(g, "1234");
				if (g == Gazetteer.TEXT) {
          area = new AreaImpl("Europe");
        }
				d.setArea(area);
				d.setEstablishmentMeans(em);
        d.setLifeStage("egg");
        d.setSeason(Season.AUTUMN);
        d.setPathway("dark alley");
        d.setYear(1966);
        d.setDegreeOfEstablishment(DegreeOfEstablishment.ESTABLISHED);
        d.setRemarks("this isn't right");
        ds.add(TestEntityGenerator.setUserDate(d));
			}
		}
		return ds;
	}
}