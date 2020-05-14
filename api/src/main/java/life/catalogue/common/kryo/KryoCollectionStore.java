package life.catalogue.common.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.InputChunked;
import com.esotericsoftware.kryo.io.OutputChunked;
import com.esotericsoftware.kryo.unsafe.UnsafeInput;
import com.esotericsoftware.kryo.unsafe.UnsafeOutput;
import com.esotericsoftware.kryo.util.Pool;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Iterator;

/**
 * Store that writes objects of the same type to a file and offers an iterator to read them all in the
 * same order as initially written.
 */
public class KryoCollectionStore<T> implements AutoCloseable, Iterable<T> {

  private final File store;
  private OutputStream outputStream;
  private OutputChunked output;
  private boolean dirty = false;
  private final Pool<Kryo> pool;
  private final Class<T> clazz;

  public KryoCollectionStore(Class<T> clazz, File store, Pool<Kryo> pool) throws FileNotFoundException {
    this.clazz = clazz;
    this.store = store;
    outputStream = new UnsafeOutput(new FileOutputStream(store));
    this.pool = pool;
    output = new OutputChunked(outputStream, 1024);
  }

  public void add(T obj) {
    Kryo kryo = pool.obtain();
    try {
      dirty = true;
      kryo.writeObject(output, obj);
      output.endChunk();
    } finally {
      pool.free(kryo);
    }
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    if (dirty) {
      flush();
    }
    return new StoreIterator();
  }

  class StoreIterator implements Iterator<T> {
    private final InputChunked input;
    private final UnsafeInput inputStream;

    public StoreIterator() {
      try {
        inputStream = new UnsafeInput(new FileInputStream(store));
        input = new InputChunked(inputStream, 1024);
      } catch (FileNotFoundException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public boolean hasNext() {
      try {
        return input.available() > 0;
      } catch (IOException e) {
        return false;
      }
    }

    @Override
    public T next() {
      Kryo kryo = pool.obtain();
      try {
        // Read data from first set of chunks...
        T obj = kryo.readObject(input, clazz);
        input.nextChunk();
        return obj;

      } finally {
        pool.free(kryo);
      }
    }

    private void close() throws Exception {
      input.close();
      inputStream.close();
    }
  }

  public void flush() {
    output.flush();
    dirty = false;
  }

  public void close() throws Exception {
    output.close();
    outputStream.close();
  }
}
