package life.catalogue.dw.jersey.writers;

import com.fasterxml.jackson.databind.ObjectWriter;

import de.undercouch.citeproc.csl.CSLItemData;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.Reference;
import life.catalogue.common.csl.CslDataConverter;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.ws.MoreMediaTypes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import de.undercouch.citeproc.csl.CSLType;

/**
 * Writer that generates BibTeX for reference instances.
 */
@Produces({MoreMediaTypes.APP_JSON_CSL})
@Provider
public class ReferenceCslBodyWriter implements MessageBodyWriter<Reference> {
  private static ObjectWriter JSON_WRITER = ApiModule.MAPPER.writerFor(CSLItemData.class);

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Reference.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Reference ref, Class<?> aClass, Type type, Annotation[] annotations, MediaType mt, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException, WebApplicationException {
    JSON_WRITER.writeValue(out, ReferenceBibtexBodyWriter.toCSL(ref));
  }
}