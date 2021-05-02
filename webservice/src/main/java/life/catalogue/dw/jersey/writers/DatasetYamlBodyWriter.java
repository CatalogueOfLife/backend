package life.catalogue.dw.jersey.writers;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.exporter.DatasetYamlWriter;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Writer that generates YAML metadata for datasets.
 */
@Produces({MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
@Provider
public class DatasetYamlBodyWriter implements MessageBodyWriter<ArchivedDataset> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return ArchivedDataset.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(ArchivedDataset dataset, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    DatasetYamlWriter.write(dataset, out);
  }

}