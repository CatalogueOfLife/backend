package life.catalogue.dw.jersey.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kotlin.Metadata;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.importer.coldp.ColdpInserter;
import life.catalogue.importer.coldp.MetadataParser;
import life.catalogue.importer.dwca.EmlParser;
import org.apache.poi.ss.formula.functions.T;
import org.gbif.dwc.terms.TermFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * @author phaneesh
 */
@Provider
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML, MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
public class DatasetMessageBodyReader implements MessageBodyReader<Dataset> {

  private static final ObjectReader DATASET_JSON_READER;
  static {
    ObjectMapper OM = new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .registerModule(new JavaTimeModule())
      .registerModule(new MetadataParser.ColdpMetadataModule());
    DATASET_JSON_READER = OM.readerFor(Dataset.class);
  }

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE) ||
      mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE) ||
      mediaType.isCompatible(MediaType.TEXT_XML_TYPE) ||
      mediaType.isCompatible(MoreMediaTypes.APP_YAML_TYPE) ||
      mediaType.isCompatible(MoreMediaTypes.TEXT_YAML_TYPE);
  }

  @Override
  public Dataset readFrom(Class<Dataset> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    if (mediaType.getSubtype().toLowerCase().contains("yaml")) {
      return MetadataParser.readMetadata(entityStream).map(DatasetWithSettings::getDataset).orElse(null);

    } else if (mediaType.getSubtype().equalsIgnoreCase("xml")) {
      return EmlParser.parse(entityStream).map(DatasetWithSettings::getDataset).orElse(null);

    } else {
      return DATASET_JSON_READER.readValue(entityStream);
    }
  }

}