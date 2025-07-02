package life.catalogue.resources;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.common.datapackage.DataPackage;
import life.catalogue.common.datapackage.DataPackageBuilder;
import life.catalogue.common.io.HttpUtils;
import life.catalogue.common.io.Resources;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Hidden
@Path("/datapackage")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DataPackageResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DataPackageResource.class);
  private final DataPackageBuilder builder = new DataPackageBuilder();
  private final String html;

  public DataPackageResource(HttpUtils http) {
    String doc;
    try {
      doc = http.get(URI.create("https://catalogueoflife.github.io/coldp/"));
    } catch (Exception e) {
      LOG.error("Failed to read ColDP docs. Use cached version", e);
      doc = bundledDocs();
    }
    html = doc;
  }

  String bundledDocs() {
    try {
      return Resources.toString("coldp-docs.html");
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
  @GET
  public DataPackage buildPackage(@BeanParam PackageDescriptor pd) {
    return buildPackage(pd, true);
  }

  public DataPackage buildPackage(PackageDescriptor pd, boolean downloadDocs) {
    return builder.docs(downloadDocs ? html : bundledDocs()).build(pd);
  }
}
