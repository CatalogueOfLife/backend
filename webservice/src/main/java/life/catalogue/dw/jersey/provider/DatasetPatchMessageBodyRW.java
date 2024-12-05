package life.catalogue.dw.jersey.provider;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Dataset JSON body reader that understands the difference between an explicit property given with a nil value and a missing property.
 * Missing properties will be read as true java NULL values, while the explicit JS nil will be converted into the type specific patch null value.
 * See Dataset#applyPatch method.
 */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class DatasetPatchMessageBodyRW implements MessageBodyReader<Dataset>, MessageBodyWriter<Dataset> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetPatchMessageBodyRW.class);
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return type == Dataset.class && Arrays.stream(annotations).anyMatch(a -> a.annotationType().equals(DatasetPatch.class));
  }

  @Override
  public Dataset readFrom(Class<Dataset> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    Map<String, Object> map = ApiModule.MAPPER.readValue(entityStream, MAP_TYPE);
    for (Map.Entry<String, Object> field : map.entrySet()) {
      if (field.getValue() == null && Dataset.NULL_TYPES.containsKey(field.getKey())) {
        field.setValue(Dataset.NULL_TYPES.get(field.getKey()));
      }
    }
    Dataset ad = ApiModule.MAPPER.convertValue(map, Dataset.class);
    return ad;
  }

  @Override
  public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    return isReadable(aClass, type, annotations, mediaType);
  }

  @Override
  public void writeTo(Dataset dataset, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
    var nullFields = new StringBuilder();
    for (PropertyDescriptor p : Dataset.PATCH_PROPS) {
      try {
        if (Dataset.NULL_TYPES.containsKey(p.getName())) {
          Object nullType = Dataset.NULL_TYPES.get(p.getName());
          if (nullType.equals(p.getReadMethod().invoke(dataset))) {
              p.getWriteMethod().invoke(dataset, (Object) null);
              nullFields.append(',');
              nullFields.append('"');
              nullFields.append(p.getName());
              nullFields.append('"');
              nullFields.append(":null");
          }
        }
      } catch (Exception e) {
        LOG.error("Fail to set dataset patch field {} to null", p.getName(), e);
      }
    }
    if (nullFields.length()>1) {
      var json = ApiModule.MAPPER.writeValueAsString(dataset);
      if (json != null) {
        nullFields.append('}');
        var j2 = json.replaceFirst("\\}$", nullFields.toString());
        IOUtils.write(j2, outputStream, StandardCharsets.UTF_8);
      }
    } else {
      ApiModule.MAPPER.writeValue(outputStream, dataset);
    }
  }
}