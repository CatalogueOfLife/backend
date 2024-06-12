package life.catalogue.dw.jersey.writers;

import life.catalogue.common.ws.MoreMediaTypes;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.imageio.ImageIO;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

@Produces({MoreMediaTypes.IMG_PNG})
@Provider
public class BufferedImageBodyWriter implements MessageBodyWriter<BufferedImage> {
  
  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return type == BufferedImage.class;
  }
  
  @Override
  public long getSize(BufferedImage t, Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return -1; // not used in JAX-RS 2
  }
  
  @Override
  public void writeTo(BufferedImage image, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    ImageIO.write(image, mt.getSubtype(), out);
  }
}