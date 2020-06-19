package life.catalogue.dw.jersey.writers;

import com.google.common.base.Throwables;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dw.jersey.MoreMediaTypes;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.stream.Stream;

@Produces({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_TSV, MoreMediaTypes.TEXT_CSV})
@Provider
public class TxtBodyWriter implements MessageBodyWriter<Stream<String>> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Stream.class.isAssignableFrom(type);
  }
  
  @Override
  public void writeTo(Stream<String> rows, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    BufferedWriter br = UTF8IoUtils.writerFromStream(out);
    try {
      rows.forEach(row -> {
        try {
          br.append(row);
          br.append('\n');
        } catch(Exception e) {
          Throwables.throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      });
    } finally {
      br.close();
    }
  }
}