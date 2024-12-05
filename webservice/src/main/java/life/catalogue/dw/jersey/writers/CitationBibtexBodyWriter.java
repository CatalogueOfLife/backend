package life.catalogue.dw.jersey.writers;

import life.catalogue.api.model.Citation;
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

/**
 * Writer that generates BibTeX for citations.
 */
@Produces({MoreMediaTypes.APP_BIBTEX})
@Provider
public class CitationBibtexBodyWriter implements MessageBodyWriter<Citation> {

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Citation.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Citation cit, Class<?> aClass, Type type, Annotation[] annotations, MediaType mt, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException, WebApplicationException {
    MoreMediaTypes.setUTF8ContentType(mt, headers);
    try (Writer w = UTF8IoUtils.writerFromStream(out)) {
      w.write( CslUtil.toBibTexString(cit.toCSL()) );
    }
  }

}