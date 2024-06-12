package life.catalogue.dw.jersey.writers;

import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.ws.MoreMediaTypes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Stream;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import com.google.common.base.Throwables;

@Produces({MoreMediaTypes.TEXT_TSV})
@Provider
public class TsvBodyWriter implements MessageBodyWriter<Stream<Object[]>> {
  
  @Override
  public boolean isWriteable(Class<?> clazz, Type type, Annotation[] antns, MediaType mt) {
    if (type instanceof ParameterizedType) {
      var pt = (ParameterizedType) type;
      return MoreMediaTypes.TEXT_TSV_TYPE.isCompatible(mt)
             && Stream.class.isAssignableFrom(clazz)
             && pt.getActualTypeArguments().length == 1
             && pt.getActualTypeArguments()[0] == Object[].class;
    }
    return false;
  }
  
  @Override
  public void writeTo(Stream<Object[]> rows, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException, WebApplicationException {
    MoreMediaTypes.setUTF8ContentType(mt, headers);
    BufferedWriter br = UTF8IoUtils.writerFromStream(out);
    try {
      rows.forEach(row -> {
        try {
          br.append(toTsv(row));
          br.append('\n');
        } catch(Exception e) {
          Throwables.throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      });
    } finally {
      br.close();
      rows.close();
    }
  }

  static String toTsv(Object[] row) {
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