package life.catalogue.dw.jersey.writers;

import life.catalogue.common.io.UTF8IoUtils;
import org.apache.ibatis.cursor.Cursor;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Writer that generates an JSON array based on any postgres backed cursor
 * and streams the results to the output using the main jackson API mapper.
 */
@Produces(MediaType.TEXT_PLAIN)
@Provider
public class CursorBodyTxtWriter implements MessageBodyWriter<Cursor<?>> {

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Cursor.class.isAssignableFrom(type);
  }
  
  @Override
  public void writeTo(Cursor<?> c, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    try (Writer writer = UTF8IoUtils.writerFromStream(out)) {
      c.forEach(obj -> {
        try {
          writer.write(obj.toString());
          writer.write('\n');
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    } finally {
      c.close();
    }
  }
}
