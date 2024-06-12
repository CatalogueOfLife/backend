package life.catalogue.dw.jersey.writers;

import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.ws.MoreMediaTypes;

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
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

@Produces({MoreMediaTypes.TEXT_CSV})
@Provider
public class CsvBodyWriter implements MessageBodyWriter<Stream<Object[]>> {
  private static final CsvWriterSettings SETTINGS = new CsvWriterSettings();

  @Override
  public boolean isWriteable(Class<?> clazz, Type type, Annotation[] antns, MediaType mt) {
    if (type instanceof ParameterizedType) {
      var pt = (ParameterizedType) type;
      return MoreMediaTypes.TEXT_CSV_TYPE.isCompatible(mt)
             && Stream.class.isAssignableFrom(clazz)
             && pt.getActualTypeArguments().length == 1
             && pt.getActualTypeArguments()[0] == Object[].class;
    }
    return false;
  }
  
  @Override
  public void writeTo(Stream<Object[]> rows, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    CsvWriter csv = new CsvWriter(UTF8IoUtils.writerFromStream(out), SETTINGS);
    try {
      rows.forEach(row -> {
        try {
          csv.writeRow(row);
        } catch(Exception e) {
          Throwables.throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      });
    } finally {
      csv.close();
      rows.close();
    }
  }
}