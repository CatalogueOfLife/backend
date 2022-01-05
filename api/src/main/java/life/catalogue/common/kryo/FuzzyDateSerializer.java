package life.catalogue.common.kryo;

import life.catalogue.common.date.FuzzyDate;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class FuzzyDateSerializer extends Serializer<FuzzyDate> {

  public FuzzyDateSerializer() {
    setImmutable(true);
  }

  @Override
  public void write(final Kryo kryo, final Output output, final FuzzyDate fd) {
    output.writeInt(fd.toInt(), true);
  }

  @Override
  public FuzzyDate read(final Kryo kryo, final Input input, final Class<? extends FuzzyDate> clazz) {
    return FuzzyDate.fromInt(input.readInt(true));
  }
}