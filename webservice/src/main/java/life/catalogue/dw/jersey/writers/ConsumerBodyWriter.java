package life.catalogue.dw.jersey.writers;

import life.catalogue.api.jackson.ApiModule;
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
 * Writer that generates an JSON array based on any postgres backed cursor
 * and streams the results to the output using the main jackson API mapper.
 */
@Produces(MediaType.APPLICATION_JSON)
@Provider
public class ConsumerBodyWriter implements MessageBodyWriter<Cursor<?>> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Cursor.class.isAssignableFrom(type);
  }
  
  @Override
  public void writeTo(Cursor<?> c, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    out.write('[');
    boolean first = true;
    for (Object sn : c) {
      if (first) {
        first = false;
      } else {
        out.write(',');
        out.write('\n');
      }
      ApiModule.MAPPER.writeValue(out, sn);
    }
    out.write(']');
  }
}