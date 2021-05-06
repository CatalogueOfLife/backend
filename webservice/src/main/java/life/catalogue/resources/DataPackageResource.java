package life.catalogue.resources;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.common.datapackage.DataPackage;
import life.catalogue.common.datapackage.DataPackageBuilder;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@Path("/datapackage")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DataPackageResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DataPackageResource.class);
  private final DataPackageBuilder builder = new DataPackageBuilder();
  
  @GET
  public DataPackage buildPackage(@BeanParam PackageDescriptor pd) {
    return builder.build(pd);
  }
  
}
