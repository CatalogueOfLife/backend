package life.catalogue.dw.jersey.writers;

import life.catalogue.common.ws.MoreMediaTypes;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import com.fasterxml.jackson.core.JsonGenerator;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

/**
 * Writes the OpenAPI document as JSON or YAML with swagger's own mappers.
 * There is no generic YAML writer, and the application's JSON mapper lacks swagger's mixins,
 * so it leaked internal model fields like exampleSetFlag that are not valid OpenAPI.
 */
@Produces({MediaType.APPLICATION_JSON, MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML})
@Provider
public class OpenApiBodyWriter implements MessageBodyWriter<OpenAPI> {

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return OpenAPI.class.isAssignableFrom(type);
  }

  @Override
  public void writeTo(OpenAPI openApi, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    var mapper = mediaType.getSubtype().toLowerCase().contains("yaml") ? Yaml.mapper() : Json.mapper();
    // jersey owns the entity stream
    mapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET).writeValue(out, openApi);
  }

}
