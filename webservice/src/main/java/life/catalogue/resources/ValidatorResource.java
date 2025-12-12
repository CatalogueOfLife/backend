package life.catalogue.resources;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.common.io.HttpUtils;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.DatasetDao;
import life.catalogue.importer.ImportManager;

import org.gbif.txtree.Tree;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/validator")
@Produces(MediaType.APPLICATION_JSON)
public class ValidatorResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ValidatorResource.class);
  private final ImportManager importManager;
  private final DatasetDao ddao;
  private final HttpUtils http;

  public ValidatorResource(ImportManager importManager, DatasetDao ddao, HttpUtils http) {
    this.importManager = importManager;
    this.ddao = ddao;
    this.http = http;
  }


  @POST
  // there are many unofficial mime types around for zip and gzip
  // these can show up via the upload component of the CLB UI if used from Windows for example, so we add them all
  @Consumes({
    MediaType.APPLICATION_OCTET_STREAM,
    MoreMediaTypes.APP_GZIP, MoreMediaTypes.APP_GZIP_ALT1, MoreMediaTypes.APP_GZIP_ALT2, MoreMediaTypes.APP_GZIP_ALT3,
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  public Dataset uploadArchive(@Auth User user, InputStream archive) throws IOException {
    if (archive == null) throw new IllegalArgumentException("archive required");

    // create new temp dataset
    Dataset d = new Dataset();
    d.setTitle("Archive Validation by " + user);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setPrivat(true);
    d.setType(DatasetType.OTHER);
    d.setLicense(License.CC0);
    int key = ddao.createTemp(d, user.getKey());
    // validate uploaded archive
    importManager.upload(key, archive, false, null, null, user);
    return d;
  }

  @GET
  @Path("/txtree")
  public Tree.VerificationResult validateTxtTree(@QueryParam("url") URI url) throws IOException, InterruptedException {
    if (url == null) throw new IllegalArgumentException("URL of text tree document required");

    String content = http.get(url);
    return Tree.verify(new StringReader(content));
  }

  @POST
  @Path("/txtree")
  public Tree.VerificationResult validateTxtTreeDoc(String content) throws IOException {
    return Tree.verify(new StringReader(content));
  }
}
