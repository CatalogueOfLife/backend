package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Media;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.MediaType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class MediaMapperTest extends TaxonExtensionMapperTest<Media, MediaMapper> {

	public MediaMapperTest() {
		super(MediaMapper.class);
	}
	
	@Override
	List<Media> createTestEntities() {
		List<Media> ds = new ArrayList<>();
    for (MediaType t : MediaType.values()) {
      for (License l: License.values()) {
        Media d = new Media();
        d.setType(t);
        d.setFormat("Etymology");
        d.setTitle(RandomUtils.randomLatinString(100));
        d.setCaptured(LocalDate.now());
        d.setCapturedBy(RandomUtils.randomLatinString(20));
        d.setLicense(l);
        d.setLink(RandomUtils.randomUri());
        d.setUrl(RandomUtils.randomUri());
        d.setRemarks("this isn't right");
        ds.add(TestEntityGenerator.setUserDate(d));
      }
    }
		return ds;
	}
}