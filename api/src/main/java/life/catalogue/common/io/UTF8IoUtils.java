package life.catalogue.common.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;

import com.google.common.io.Files;

public class UTF8IoUtils {
  
  public static BufferedWriter writerFromGzipFile(File f) throws IOException {
    Files.createParentDirs(f);
    return writerFromStream(new GZIPOutputStream(new FileOutputStream(f)));
  }

  public static BufferedWriter writerFromFile(File f) throws IOException {
    Files.createParentDirs(f);
    return writerFromStream(new FileOutputStream(f));
  }

  public static BufferedWriter writerFromPath(Path p) throws IOException {
    java.nio.file.Files.createDirectories(p.getParent());
    return writerFromStream(java.nio.file.Files.newOutputStream(p));
  }
  
  public static BufferedWriter writerFromStream(OutputStream stream) {
    return new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
  }
  
  public static BufferedReader readerFromGzipFile(File f) throws IOException {
    return readerFromStream(new GZIPInputStream(new FileInputStream(f)));
  }

  public static BufferedReader readerFromFile(File f) throws IOException {
    return readerFromStream(new FileInputStream(f));
  }
  
  public static BufferedReader readerFromStream(InputStream stream) {
    return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
  }

  public static BufferedReader readerFromString(String string) {
    return new BufferedReader(new StringReader(string));
  }

  public static String readString(File f) throws IOException {
    return readString(new FileInputStream(f));
  }

  /**
   * Reads the entire stream into a string and always closes the input stream!
   */
  public static String readString(InputStream stream) throws IOException {
    try {
      return IOUtils.toString(stream, StandardCharsets.UTF_8);
    } finally {
      stream.close();
    }
  }

}
