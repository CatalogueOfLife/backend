package life.catalogue.dw.jersey.writers;

import life.catalogue.api.model.Dataset;
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

/**
 * Writer that generates BibTeX for datasets.
 */
@Produces({MoreMediaTypes.APP_BIBTEX})
@Provider
public class DatasetBibtexBodyWriter implements MessageBodyWriter<Dataset> {

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Dataset.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(Dataset dataset, Class<?> aClass, Type type, Annotation[] annotations, MediaType mt, MultivaluedMap<String, Object> headers, OutputStream out) throws IOException, WebApplicationException {
    TxtBodyWriter.setUTF8ContentType(mt, headers);
    try (Writer w = UTF8IoUtils.writerFromStream(out)) {
      w.write( CslUtil.toBibTexString(dataset.toCSL()) );
    }
  }

}