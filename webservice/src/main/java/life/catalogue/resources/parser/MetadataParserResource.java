package life.catalogue.resources.parser;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.MetadataFormat;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dw.jersey.provider.DatasetMessageBodyReader;
import life.catalogue.metadata.coldp.ColdpMetadataParser;
import life.catalogue.metadata.datacite.DataciteParser;
import life.catalogue.metadata.eml.EmlParser;
import life.catalogue.metadata.zenodo.ZenodoParser;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/parser/metadata")
@Produces({MediaType.APPLICATION_JSON, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
public class MetadataParserResource {

  private static Optional<Dataset> parseAny(InputStream stream, MetadataFormat format) throws Exception {
    switch (ObjectUtils.coalesce(format, MetadataFormat.YAML)) {
      case YAML:
        return ColdpMetadataParser.readYAML(stream).map(DatasetWithSettings::getDataset);
      case EML:
        return EmlParser.parse(stream).map(DatasetWithSettings::getDataset);
      case DATACITE:
        return DataciteParser.parse(stream).map(DatasetWithSettings::getDataset);
      case ZENODO:
        return ZenodoParser.parse(stream).map(DatasetWithSettings::getDataset);
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
   * curl POST -H "Content-Type:text/yaml" --data-binary @metadata.yaml http://api.checklistbank.org/parser/metadata
   * </pre>
   */
  @POST
  @Consumes({MediaType.APPLICATION_JSON,
    MediaType.TEXT_PLAIN,
    MediaType.APPLICATION_XML, MediaType.TEXT_XML,
    MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML
  })
  public Optional<Dataset> parsePost(InputStream data, @QueryParam("format") MetadataFormat format, @Context ContainerRequestContext ctx) throws Exception {
    if (format == null && ctx.getMediaType() != null) {
      // detect by content type
      format = DatasetMessageBodyReader.parseType(ctx.getMediaType());
    }
    return parseAny(data, format);
  }

}
