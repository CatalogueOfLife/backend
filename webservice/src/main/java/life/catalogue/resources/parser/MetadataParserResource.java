package life.catalogue.resources.parser;

import com.google.common.collect.Lists;
import io.dropwizard.auth.Auth;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.MetadataFormat;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.provider.DatasetMessageBodyReader;
import life.catalogue.importer.coldp.MetadataParser;
import life.catalogue.importer.dwca.EmlParser;
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
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
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

  private static Optional<Dataset> parseAny(InputStream stream, MetadataFormat format) throws Exception {
    switch (ObjectUtils.coalesce(format, MetadataFormat.YAML)) {
      case YAML:
        return MetadataParser.readMetadata(stream).map(DatasetWithSettings::getDataset);
      case EML:
        return EmlParser.parse(stream).map(DatasetWithSettings::getDataset);
      default:
        return Optional.of(ApiModule.MAPPER.readValue(stream, Dataset.class));
    }
  }

  /**
   * Parsing metadata hosted on a URL given as a GET query parameters.
   */
  @GET
  public Optional<Dataset> parseGet(@QueryParam("url") String url, @QueryParam("format") MetadataFormat format) throws Exception {
    return parseAny(new URL(url).openStream(), format);
  }
  
  /**
   * Parses metadata POSTed directly as a YAML file.
   * <pre>
   * curl POST -H "Content-Type:text/yaml" --data-binary @metadata.yaml http://api.catalogueoflife.org/parser/metadata
   * </pre>
   */
  @POST
  @Consumes({MediaType.APPLICATION_JSON,
    MediaType.TEXT_PLAIN,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML
  })
  public Optional<Dataset> parsePost(InputStream data, @QueryParam("format") MetadataFormat format, @Context ContainerRequestContext ctx) throws Exception {
    if (format == null && ctx.getMediaType() != null) {
      // detect by content type
      format = DatasetMessageBodyReader.parseType(ctx.getMediaType());
    }
    return parseAny(data, format);
  }
  
  /**
   * Parses metadata uploaded as plain UTF-8 YAML file.
   * <pre>
   * curl -F metadata=@metadata.yaml http://api.catalogueoflife.org/parser/metadata
   * </pre>
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Optional<Dataset> parseFile(@FormDataParam("metadata") InputStream data, @QueryParam("format") MetadataFormat format) throws Exception {
    if (data == null) {
      throw new IllegalArgumentException("No metadata uploaded");
    }
    return parseAny(data, format);
  }

}
