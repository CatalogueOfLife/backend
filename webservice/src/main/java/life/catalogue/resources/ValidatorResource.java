package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.dropwizard.auth.Auth;

@Path("/validator")
@Produces(MediaType.APPLICATION_JSON)
public class ValidatorResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ValidatorResource.class);
  private final ImportManager importManager;
  private final DatasetDao ddao;

  public ValidatorResource(ImportManager importManager, DatasetDao ddao) {
    this.importManager = importManager;
    this.ddao = ddao;
  }


  @POST
  // there are many unofficial mime types around for zip and gzip
  // these can show up via the upload component of the CLB UI if used from Windows for example, so we add them all
  @Consumes({
    MediaType.APPLICATION_OCTET_STREAM,
    MoreMediaTypes.APP_GZIP, MoreMediaTypes.APP_GZIP_ALT1, MoreMediaTypes.APP_GZIP_ALT2, MoreMediaTypes.APP_GZIP_ALT3,
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  public Dataset uploadArchive(@Auth User user, @Context HttpHeaders headers, InputStream archive) throws IOException {
    if (archive == null) throw new IllegalArgumentException("archive required");

    String fn = ResourceUtils.filenameFromHeaders(headers);
    // create new temp dataset
    Dataset d = new Dataset();
    d.setTitle("Archive Validation "+fn);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setPrivat(true);
    d.setType(DatasetType.OTHER);
    d.setLicense(License.CC0);
    int key = ddao.createTemp(d, user.getKey());
    // validate uploaded archive
    importManager.upload(key, archive, false, fn, null, user);
    return d;
  }
}
