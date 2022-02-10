package life.catalogue.dw.jersey.provider;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.MetadataFormat;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.metadata.coldp.ColdpMetadataParser;
import life.catalogue.metadata.eml.EmlParser;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

/**
 * Dataset body reader that understands YAML or XML given as EML.
 */
@Provider
@Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
public class DatasetMessageBodyReader implements MessageBodyReader<Dataset> {

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE) ||
      mediaType.isCompatible(MediaType.TEXT_XML_TYPE) ||
      mediaType.isCompatible(MoreMediaTypes.APP_YAML_TYPE) ||
      mediaType.isCompatible(MoreMediaTypes.TEXT_YAML_TYPE);
  }

  @Override
  public Dataset readFrom(Class<Dataset> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    switch (parseType(mediaType)) {
      case YAML:
        return ColdpMetadataParser.readYAML(entityStream).map(DatasetWithSettings::getDataset).orElse(null);
      case EML:
        return EmlParser.parse(entityStream).map(DatasetWithSettings::getDataset).orElse(null);
      default:
        throw new IllegalStateException("I should only ever deal with YAML and EML");
    }
  }

  public static MetadataFormat parseType(MediaType mediaType) {
    if (mediaType.getSubtype().toLowerCase().contains("yaml")) {
      return MetadataFormat.YAML;
    } else if (mediaType.getSubtype().toLowerCase().contains("xml")) {
      return MetadataFormat.EML;
    } else {
      return MetadataFormat.JSON;
    }
  }

}