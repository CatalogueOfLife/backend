package org.col.db.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.col.api.RandomUtils;
import org.col.api.model.Media;
import org.col.api.vocab.License;
import org.col.api.vocab.MediaType;

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
        d.setTitle(RandomUtils.randomString(100));
        d.setCreated(LocalDate.now());
        d.setCreator(RandomUtils.randomString(20));
        d.setLicense(l);
        d.setLink(RandomUtils.randomUri());
        d.setUrl(RandomUtils.randomUri());
        ds.add(d);
      }
    }
		return ds;
	}
}