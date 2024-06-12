package life.catalogue.dw.jersey.writers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;

/**
 * @author phaneesh
 */
@Provider
@Consumes(MediaType.APPLICATION_XML)
@Produces(MediaType.APPLICATION_XML)
public class XmlMessageBodyProvider<T> implements MessageBodyReader<T>, MessageBodyWriter<T> {

  public static final XmlMapper objectMapper = new XmlMapper();

  public XmlMessageBodyProvider() {
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, true);
    objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    objectMapper.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
  }

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE);
  }

  @Override
  public boolean isWriteable(Class<?> type, Type genericType,
                             Annotation[] annotations, MediaType mediaType) {
    return mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE);
  }

  @Override
  public T readFrom(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    return objectMapper.readValue(entityStream, type);
  }

  @Override
  public void writeTo(Object o, Class aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
    outputStream.write(objectMapper.writeValueAsBytes(o));
  }

  @Override
  public long getSize(Object o, Class aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }
}