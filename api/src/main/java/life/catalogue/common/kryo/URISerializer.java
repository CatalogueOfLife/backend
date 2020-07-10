package life.catalogue.common.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.net.URI;

public class URISerializer extends Serializer<URI> {

  public URISerializer() {
    setImmutable(true);
  }

  @Override
  public void write(final Kryo kryo, final Output output, final URI uri) {
    output.writeString(uri.toString());
  }

  @Override
  public URI read(final Kryo kryo, final Input input, final Class<? extends URI> uriClass) {
    return URI.create(input.readString());
  }

  @Override
  public URI copy(Kryo kryo, URI original) {
    return URI.create(original.toString());
  }
}