package life.catalogue.dw.logging;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;

import ch.qos.logback.access.spi.IAccessEvent;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

public class LoggerNameJsonProvider extends AbstractFieldJsonProvider<IAccessEvent> {

  final static String LOGGER_NAME = "http.request";

  public LoggerNameJsonProvider() {
    setFieldName("logger_name");
  }


  public void writeTo(JsonGenerator generator, IAccessEvent event) throws IOException {
    JsonWritingUtils.writeStringField(generator, getFieldName(), LOGGER_NAME);
  }

}
