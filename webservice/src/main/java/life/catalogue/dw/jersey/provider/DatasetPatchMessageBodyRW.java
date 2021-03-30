package life.catalogue.dw.jersey.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.MetadataFormat;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;
import life.catalogue.importer.coldp.MetadataParser;
import life.catalogue.importer.dwca.EmlParser;
import life.catalogue.jackson.YamlMapper;
import org.checkerframework.checker.units.qual.K;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.annotation.meta.field;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * ArchivedDataset JSON body reader that understands the difference between an explicit property given with a nil value and a missing property.
 * Missing properties will be read as true java NULL values, while the explicit JS nil will be converted into the type specific patch null value.
 * See ArchivedDataset#applyPatch method.
 */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class DatasetPatchMessageBodyRW implements MessageBodyReader<ArchivedDataset>, MessageBodyWriter<ArchivedDataset> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetPatchMessageBodyRW.class);
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return type == ArchivedDataset.class && Arrays.stream(annotations).anyMatch(a -> a.annotationType().equals(DatasetPatch.class));
  }

  @Override
  public ArchivedDataset readFrom(Class<ArchivedDataset> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    Map<String, Object> map = ApiModule.MAPPER.readValue(entityStream, MAP_TYPE);
    for (Map.Entry<String, Object> field : map.entrySet()) {
      if (field.getValue() == null && ArchivedDataset.NULL_TYPES.containsKey(field.getKey())) {
        field.setValue(ArchivedDataset.NULL_TYPES.get(field.getKey()));
      }
    }
    ArchivedDataset ad = ApiModule.MAPPER.convertValue(map, ArchivedDataset.class);
    return ad;
  }

  @Override
  public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    return isReadable(aClass, type, annotations, mediaType);
  }

  @Override
  public void writeTo(ArchivedDataset dataset, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
    for (PropertyDescriptor p : ArchivedDataset.METADATA_PROPS) {
      try {
        if (ArchivedDataset.NULL_TYPES.containsKey(p.getName())) {
          Object nullType = ArchivedDataset.NULL_TYPES.get(p.getName());
          if (nullType.equals(p.getReadMethod().invoke(dataset))) {
              p.getWriteMethod().invoke(dataset, (Object) null);
          }
        }
      } catch (Exception e) {
        LOG.error("Fail to set dataset patch field {} to null", p.getName(), e);
      }
    }
    ApiModule.MAPPER.writeValue(outputStream, dataset);
  }
}