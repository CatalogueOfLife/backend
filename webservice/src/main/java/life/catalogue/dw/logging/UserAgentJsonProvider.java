package life.catalogue.dw.logging;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

import java.io.IOException;
import java.util.regex.Pattern;

public class UserAgentJsonProvider extends AbstractFieldJsonProvider<IAccessEvent> {

  final static String HEADER = "User-Agent";
  final static Pattern IN_BRACKETS = Pattern.compile("\\s*\\([^()]+\\)");

  public UserAgentJsonProvider() {
    setFieldName("user-agent");
  }

  public String[] normalize(String agent) {
    // "user-agent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.1 Safari/605.1.15"
    // Mozilla/5.0
    // AppleWebKit/605.1.15
    // Version/14.0.1
    // Safari/605.1.15"
    if (agent != null) {
      agent = IN_BRACKETS.matcher(agent).replaceAll("");
      return agent.split(" ");
    }
    return null;
  }

  public void writeTo(JsonGenerator generator, IAccessEvent event) throws IOException {
    if (event.getRequestHeaderMap().containsKey(HEADER)) {
      String[] agents = normalize(event.getRequestHeaderMap().get(HEADER));
      JsonWritingUtils.writeStringArrayField(generator, getFieldName(), agents);
    }
  }

}
