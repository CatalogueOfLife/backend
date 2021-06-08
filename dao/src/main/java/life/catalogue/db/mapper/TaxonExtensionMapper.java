package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SectorScopedEntity;
import life.catalogue.api.model.TaxonExtension;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.TaxonProcessable;

import org.apache.ibatis.annotations.Param;

public interface TaxonExtensionMapper<T extends SectorScopedEntity<Integer>>
  extends DatasetProcessable<TaxonExtension<T>>, SectorProcessable<T>, TaxonProcessable<T>, CopyDataset {

	T get(@Param("key") DSID<Integer> key);

	void create(@Param("obj") T object,
              @Param("taxonId") String taxonId);
	
}
