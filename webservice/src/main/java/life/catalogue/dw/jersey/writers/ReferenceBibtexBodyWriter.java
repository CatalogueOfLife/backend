package life.catalogue.dw.jersey.writers;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.Reference;
import life.catalogue.common.csl.CslDataConverter;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dw.jersey.MoreMediaTypes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import de.undercouch.citeproc.csl.CSLType;

/**
 * Writer that generates BibTeX for reference instances.
 */
@Produces({MoreMediaTypes.APP_BIBTEX})
@Provider
public class ReferenceBibtexBodyWriter implements MessageBodyWriter<Reference> {

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Reference.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Reference ref, Class<?> aClass, Type type, Annotation[] annotations, MediaType mt, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException, WebApplicationException {
    TxtBodyWriter.setUTF8ContentType(mt, headers);
    try (Writer w = UTF8IoUtils.writerFromStream(out)) {
      CslData csl;
      if (ref.getCsl() != null) {
        csl = ref.getCsl();
      } else {
        csl = new CslData();
        csl.setType(CSLType.WEBPAGE); // will become MISC in bibtex
        csl.setTitle(ref.getCitation());
      }
      csl.setId(ref.getId());
      w.write( CslUtil.toBibTexString(CslDataConverter.toCSLItemData(csl)));
    }
  }
}