package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.TaxonExtension;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaxonExtensionMapper<T extends DatasetScopedEntity<Integer>> extends DatasetProcessable<TaxonExtension<T>>, CopyDataset {

	T get(@Param("key") DSID<Integer> key);
	
	List<T> listByTaxon(@Param("key") DSID<String> key);
	
	void create(@Param("obj") T object,
              @Param("taxonId") String taxonId);
	
}
