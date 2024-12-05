package life.catalogue.dw.jersey.writers;

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

import org.apache.ibatis.cursor.Cursor;

/**
 * Writer that generates an JSON array based on any postgres backed cursor
 * and streams the results to the output using the main jackson API mapper.
 */
@Produces(MediaType.APPLICATION_JSON)
@Provider
public class CursorBodyJsonWriter implements MessageBodyWriter<Cursor<?>> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Cursor.class.isAssignableFrom(type);
  }
  
  @Override
  public void writeTo(Cursor<?> c, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    try (StreamBodyJsonWriter.JsonArrayConsumer consumer = new StreamBodyJsonWriter.JsonArrayConsumer(out)){
      c.forEach(consumer);
    } finally {
      c.close();
    }
  }
}