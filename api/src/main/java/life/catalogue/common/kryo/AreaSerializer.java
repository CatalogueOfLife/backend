package life.catalogue.common.kryo;

import life.catalogue.api.vocab.*;

import org.gbif.dwc.terms.TermFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class AreaSerializer extends Serializer<Area> {
  private static final Logger LOG = LoggerFactory.getLogger(AreaSerializer.class);
  private static final Pattern PREFIX  = Pattern.compile("^([a-z]+)\\s*:\\s*(.+)$");

  public AreaSerializer() {
    // dont accept null values
    super(false);
  }

  @Override
  public void write(Kryo kryo, Output output, Area area) {
    if (area.getGazetteer() == Gazetteer.TEXT) {
      output.writeString(area.getName());
    } else {
      output.writeString(area.getGlobalId());
    }
  }

  @Override
  public Area read(Kryo kryo, Input input, Class<? extends Area> aClass) {
    String value = input.readString();
    try {
      return parse(value);
    } catch (IllegalArgumentException e) {
      LOG.warn("Unknown area scheme or bad enumeration: {}", value, e);
      return new AreaImpl(value);
    }
  }

  @Override
  public Area copy(Kryo kryo, Area original) {
    return original;
  }

  public static Area parse(String prefixedIdOrName) throws IllegalArgumentException {
    var m = PREFIX.matcher(prefixedIdOrName);
    if (m.find()) {
      final Gazetteer standard = Gazetteer.of(m.group(1));
      final String value = m.group(2).trim();
      switch (standard) {
        case ISO:
          return Country.fromIsoCode(value).orElseThrow(() -> new IllegalArgumentException(value + " is no supported ISO country code"));
        case TDWG:
          return TdwgArea.of(value);
        case LONGHURST:
          return LonghurstArea.of(value);
        default:
          // we have not implemented other area enumerations yet!
          return new AreaImpl(standard, value, null);
      }
    } else {
      return new AreaImpl(prefixedIdOrName);
    }
  }
}