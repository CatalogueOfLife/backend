package life.catalogue.dw.jersey.writers;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Citation;
import life.catalogue.common.ws.MoreMediaTypes;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import com.fasterxml.jackson.databind.ObjectWriter;

import de.undercouch.citeproc.csl.CSLItemData;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

/**
 * Writer that generates CSL-JSON for citations.
 */
@Produces({MoreMediaTypes.APP_JSON_CSL})
@Provider
public class CitationCslBodyWriter implements MessageBodyWriter<Citation> {
  private static ObjectWriter JSON_WRITER = ApiModule.MAPPER.writerFor(CSLItemData.class);

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Citation.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Citation cit, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    JSON_WRITER.writeValue(out, cit.toCSL());
  }

}