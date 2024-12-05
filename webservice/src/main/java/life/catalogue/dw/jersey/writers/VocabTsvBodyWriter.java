package life.catalogue.dw.jersey.writers;

import com.google.common.base.Throwables;

import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.ws.MoreMediaTypes;

import org.glassfish.jersey.message.internal.MediaTypes;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Vocabularies are listed as list of maps.
 * Allow them to be turned into TSV files!
 */
@Produces({MoreMediaTypes.TEXT_TSV, MoreMediaTypes.TEXT_WILDCARD})
@Provider
public class VocabTsvBodyWriter implements MessageBodyWriter<List<Map<String, Object>>> {
  
  @Override
  public boolean isWriteable(Class<?> clazz, Type type, Annotation[] antns, MediaType mt) {
    if (MoreMediaTypes.TEXT_TSV_TYPE.isCompatible(mt)
      && type instanceof ParameterizedType
      && List.class.isAssignableFrom(clazz)
    ) {
      var pt1 = (ParameterizedType) type;
      if (pt1.getActualTypeArguments().length == 1
        && pt1.getActualTypeArguments()[0] instanceof ParameterizedType
      ) {
        var pt2 = (ParameterizedType) pt1.getActualTypeArguments()[0];
        if (pt2.getActualTypeArguments().length == 2
          && pt2.getRawType() instanceof Class
          && pt2.getActualTypeArguments()[0] instanceof Class) {
          var cl = (Class) pt2.getRawType();
          var keyCl = (Class) pt2.getActualTypeArguments()[0];
          return Map.class.isAssignableFrom(cl) && String.class.isAssignableFrom(keyCl);
        }
      }
    }
    return false;
  }
  
  @Override
  public void writeTo(List<Map<String, Object>> rows, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException, WebApplicationException {
    MoreMediaTypes.setUTF8ContentType(mt, headers);
    BufferedWriter br = UTF8IoUtils.writerFromStream(out);
    // setup column map based on all rows, as some props might be optional
    int idx = 0;
    Map<String, Integer> cols = new HashMap<>();
    for (var row : rows) {
      for (var key : row.keySet()) {
        if (!cols.containsKey(key)) {
          cols.put(key, idx);
          idx++;
        }
      }
    }
    final int size = cols.size();

    // header
    String[] row = new String[size];
    for (var col : cols.entrySet()) {
      row[col.getValue()] = col.getKey();
    }
    br.append(TsvBodyWriter.toTsv(row));
    br.append('\n');

    // content
    try {
      for (var map : rows) {
        try {
          row = new String[size];
          for (var col : cols.entrySet()) {
            Object val = map.get(col.getKey());
            if (val != null) {
              row[col.getValue()] = val.toString();
            }
          }
          br.append(TsvBodyWriter.toTsv(row));
          br.append('\n');
        } catch(Exception e) {
          Throwables.throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      }
    } finally {
      br.close();
    }
  }
}