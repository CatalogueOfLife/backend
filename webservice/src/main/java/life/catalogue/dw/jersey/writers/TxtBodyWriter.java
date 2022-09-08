package life.catalogue.dw.jersey.writers;

import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dw.jersey.MoreMediaTypes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Stream;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.google.common.base.Throwables;

@Produces({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_TSV, MoreMediaTypes.TEXT_CSV})
@Provider
public class TxtBodyWriter implements MessageBodyWriter<Stream<String>> {

  static void setUTF8ContentType(MediaType mt, MultivaluedMap<String, Object> headers) {
    headers.putSingle(HttpHeaders.CONTENT_TYPE, mt.toString() + ";charset=UTF-8");
  }

  @Override
  public boolean isWriteable(Class<?> clazz, Type type, Annotation[] antns, MediaType mt) {
    if (type instanceof ParameterizedType) {
      var pt = (ParameterizedType) type;
      var b = MediaType.TEXT_PLAIN_TYPE.isCompatible(mt)
              && Stream.class.isAssignableFrom(clazz)
              && pt.getActualTypeArguments().length == 1
              && pt.getActualTypeArguments()[0] == String.class;
      return b;
    }
    return false;
  }
  
  @Override
  public void writeTo(Stream<String> rows, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException, WebApplicationException {
    setUTF8ContentType(mt, headers);
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
      rows.close();
    }
  }
}