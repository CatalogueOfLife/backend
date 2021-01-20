package life.catalogue.resources.parser;

import com.google.common.collect.Lists;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.vocab.Issue;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.importer.coldp.MetadataParser;
import life.catalogue.parser.NameParser;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/parser/metadata")
@Produces(MediaType.APPLICATION_JSON)
public class MetadataParserResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(MetadataParserResource.class);


  /**
   * Parsing metadata hosted on a URL given as a GET query parameters.
   */
  @GET
  public Optional<DatasetWithSettings> parseGet(@QueryParam("url") String url) throws IOException {
    return MetadataParser.readMetadata(new URL(url).openStream());
  }
  
  /**
   * Parses metadata POSTed directly as a YAML file.
   * <pre>
   * curl POST -H "Content-Type:text/yaml" --data-binary @metadata.yaml http://api.catalogueoflife.org/parser/metadata
   * </pre>
   */
  @POST
  @Consumes({MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML, MediaType.TEXT_PLAIN,
    MediaType.APPLICATION_JSON // we include JSON as this is the default if no Accept header is given
  })
  public Optional<DatasetWithSettings> parsePost(InputStream data) {
    return MetadataParser.readMetadata(data);
  }
  
  /**
   * Parses metadata uploaded as plain UTF-8 YAML file.
   * <pre>
   * curl -F metadata=@metadata.yaml http://api.catalogueoflife.org/parser/metadata
   * </pre>
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Optional<DatasetWithSettings> parseFile(@FormDataParam("metadata") InputStream data) throws UnsupportedEncodingException {
    if (data == null) {
      throw new IllegalArgumentException("No metadata uploaded");
    }
    return MetadataParser.readMetadata(data);
  }

}
