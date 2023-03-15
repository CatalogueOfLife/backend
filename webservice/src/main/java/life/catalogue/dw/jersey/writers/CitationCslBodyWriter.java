package life.catalogue.dw.jersey.writers;

import com.fasterxml.jackson.databind.ObjectWriter;

import de.undercouch.citeproc.csl.CSLItemData;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.ws.MoreMediaTypes;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

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