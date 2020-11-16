package life.catalogue.dw.logging;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

import java.io.IOException;

public class HeaderJsonProvider extends AbstractFieldJsonProvider<IAccessEvent> {

  final String key;

  public HeaderJsonProvider(String header) {
    this(header, header.toLowerCase());
  }

  public HeaderJsonProvider(String header, String fieldName) {
    key = header;
    setFieldName(fieldName);
  }

  public void writeTo(JsonGenerator generator, IAccessEvent event) throws IOException {
    if (event.getRequestHeaderMap().containsKey(key)) {
      JsonWritingUtils.writeStringField(generator, getFieldName(), event.getRequestHeaderMap().get(key));
    }
  }

}
