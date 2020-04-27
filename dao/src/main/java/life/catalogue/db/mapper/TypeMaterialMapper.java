package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 *
 */
public interface TypeMaterialMapper extends CRUD<DSID<String>, TypeMaterial>, DatasetProcessable<TypeMaterial> {

	List<TypeMaterial> listByName(@Param("key") DSID<String> key);

}
