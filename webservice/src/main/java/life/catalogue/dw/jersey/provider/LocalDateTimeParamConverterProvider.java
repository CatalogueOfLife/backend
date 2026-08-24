package life.catalogue.dw.jersey.provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import com.google.common.base.Strings;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

/**
 * Jersey parameter converter for LocalDateTime that also accepts a bare ISO date,
 * which is read as the start of that day. Used e.g. by the job history date range filters.
 */
@Provider
public class LocalDateTimeParamConverterProvider implements ParamConverterProvider {

  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] antns) {
    return rawType == LocalDateTime.class ? (ParamConverter<T>) new Converter() : null;
  }

  static class Converter implements ParamConverter<LocalDateTime> {

    @Override
    public LocalDateTime fromString(String value) {
      if (Strings.isNullOrEmpty(value)) return null;
      final String val = value.trim();
      try {
        return LocalDateTime.parse(val);
      } catch (DateTimeParseException e) {
        try {
          return LocalDate.parse(val).atStartOfDay();
        } catch (DateTimeParseException e2) {
          throw new BadRequestException("Invalid date or date time value: " + value);
        }
      }
    }

    @Override
    public String toString(LocalDateTime value) {
      return value == null ? null : value.toString();
    }
  }
}
