package life.catalogue.dw.jersey.writers;

import life.catalogue.api.model.Citation;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.metadata.coldp.YamlMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Writer that generates YAML metadata for citations.
 */
@Produces({MoreMediaTypes.APP_YAML, MoreMediaTypes.TEXT_YAML})
@Provider
public class CitationYamlBodyWriter implements MessageBodyWriter<Citation> {
  private static ObjectWriter WRITER = YamlMapper.MAPPER.writerFor(Citation.class);

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Citation.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Citation cit, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    WRITER.writeValue(out, cit);
  }

}