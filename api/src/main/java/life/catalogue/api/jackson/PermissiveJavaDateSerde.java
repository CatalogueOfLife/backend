package life.catalogue.api.jackson;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Lenient deserializers for LocalDate and LocalDateTime that catch DateTimeParseException and provide NULL instead.
 */
public class PermissiveJavaDateSerde {
  private static final Logger LOG = LoggerFactory.getLogger(PermissiveJavaDateSerde.class);
  static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .parseLenient()
      .append(DateTimeFormatter.ISO_LOCAL_DATE)
      .optionalStart()
        .appendLiteral('T')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .optionalStart()
          .appendOffset("+HHmm", "+0000")
        .optionalEnd()
      .optionalEnd()
      .toFormatter();

  public static class LocalDateTimeDeserializer extends com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer {
    public LocalDateTimeDeserializer() {
      super(FORMATTER);
    }

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      try {
        return super.deserialize(parser, context);
      } catch (DateTimeParseException | InvalidFormatException e) {
        LOG.warn("LocalDateTime parsing exception: {}", e.getMessage());
        return null;
      }
    }
  }

  public static class LocalDateDeserializer extends com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer {
    public LocalDateDeserializer() {
      super(FORMATTER);
    }

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      try {
        return super.deserialize(parser, context);
      } catch (DateTimeParseException | InvalidFormatException e) {
        LOG.warn("LocalDate parsing exception", e);
        return null;
      }
    }
  }

}
