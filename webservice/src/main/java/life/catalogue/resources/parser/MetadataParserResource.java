package life.catalogue.resources.parser;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.MetadataFormat;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.provider.DatasetMessageBodyReader;
import life.catalogue.importer.coldp.MetadataParser;
import life.catalogue.importer.dwca.EmlParser;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/parser/metadata")
@Produces({MediaType.APPLICATION_JSON, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
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
