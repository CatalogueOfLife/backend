package life.catalogue.resources;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.common.datapackage.DataPackage;
import life.catalogue.common.datapackage.DataPackageBuilder;

import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import life.catalogue.common.io.HttpUtils;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Hidden;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;

@Hidden
@Path("/datapackage")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DataPackageResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DataPackageResource.class);
  private final HttpUtils http;
  private final DataPackageBuilder builder = new DataPackageBuilder();
  private final String html;
  public DataPackageResource() {
    this.http = new HttpUtils();
    String doc;
    try {
      doc = http.get(URI.create("https://github.com/CatalogueOfLife/coldp/blob/master/README.md"));
    } catch (Exception e) {
      doc = "";
      LOG.error("Failed to read ColDP docs", e);
    }
    html = doc;
  }

  @GET
  public DataPackage buildPackage(@BeanParam PackageDescriptor pd) {
    return builder.docs(html).build(pd);
  }
  
}
