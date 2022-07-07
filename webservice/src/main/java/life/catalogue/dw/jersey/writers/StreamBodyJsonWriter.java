package life.catalogue.dw.jersey.writers;

import life.catalogue.api.jackson.ApiModule;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writer that generates an JSON array based on any object stream
 * and streams the results to the output using the main jackson API mapper.
 */
@Produces(MediaType.APPLICATION_JSON)
@Provider
public class StreamBodyJsonWriter implements MessageBodyWriter<Stream<?>> {
  private static final Logger LOG = LoggerFactory.getLogger(StreamBodyJsonWriter.class);

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Stream.class.isAssignableFrom(type);
  }
  
  @Override
  public void writeTo(Stream<?> stream, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    try (JsonArrayConsumer consumer = new JsonArrayConsumer(out)){
      stream.forEach(consumer);
    } finally {
      stream.close();
    }
  }

  public static class JsonArrayConsumer implements Consumer<Object>, AutoCloseable {
    private final OutputStream out;
    private boolean first = true;

    JsonArrayConsumer(OutputStream out) {
      this.out = out;
      try {
        out.write('[');
      } catch (IOException e) {
        LOG.error("Failed to write to output steam", e);
        throw new RuntimeException(e);
      }
    }

    @Override
    public void accept(Object o) {
      try {
        if (first) {
          first = false;
        } else {
          out.write(',');
          out.write('\n');
        }
        ApiModule.MAPPER.writeValue(out, o);

      } catch (IOException e) {
        LOG.error("Failed to consume object {}", o, e);
      }
    }

    @Override
    public void close() {
      try {
        out.write(']');
      } catch (IOException e) {
        LOG.error("Failed to write to output steam", e);
        throw new RuntimeException(e);
      }
    }
  }
}