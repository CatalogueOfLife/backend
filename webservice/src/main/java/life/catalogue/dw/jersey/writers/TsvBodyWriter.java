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

@Produces({MoreMediaTypes.TEXT_TSV})
@Provider
public class TsvBodyWriter implements MessageBodyWriter<Stream<Object[]>> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Stream.class.isAssignableFrom(type);
  }
  
  @Override
  public void writeTo(Stream<Object[]> rows, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    BufferedWriter br = UTF8IoUtils.writerFromStream(out);
    try {
      rows.forEach(row -> {
        try {
          br.append(toTsv(row));
        } catch(Exception e) {
          Throwables.throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      });
    } finally {
      br.close();
    }
  }

  private static String toTsv(Object[] row) {
    StringBuilder sb = new StringBuilder();
    for (Object col : row) {
      if (sb.length() > 0) {
        sb.append('\t');
      }
      if (col != null) {
        sb.append(col.toString().replaceAll("\t", " "));
      }
    }
    return sb.toString();
  }
}