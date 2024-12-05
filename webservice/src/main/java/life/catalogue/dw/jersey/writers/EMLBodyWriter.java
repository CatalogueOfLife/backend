package life.catalogue.dw.jersey.writers;

import life.catalogue.api.model.Dataset;
import life.catalogue.metadata.eml.EmlWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

/**
 * Writer that generates EML XML for datasets.
 */
@Produces({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
@Provider
public class EMLBodyWriter implements MessageBodyWriter<Dataset> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Dataset.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Dataset dataset, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    EmlWriter.write(dataset, out);
  }

}