package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 *
 */
public interface TypeMaterialMapper extends CRUD<DSID<String>, TypeMaterial>, DatasetProcessable<TypeMaterial>, SectorProcessable<TypeMaterial> {

	List<TypeMaterial> listByName(@Param("key") DSID<String> key);

}
