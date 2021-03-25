package life.catalogue.dw.jersey.writers;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.exporter.EmlWriter;
import org.apache.ibatis.cursor.Cursor;

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
 * Writer that generates EML XML for datasets.
 */
@Produces({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
@Provider
public class EMLBodyWriter implements MessageBodyWriter<ArchivedDataset> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return ArchivedDataset.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(ArchivedDataset dataset, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    EmlWriter.write(dataset, out);
  }

}