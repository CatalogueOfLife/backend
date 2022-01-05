package life.catalogue.common.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

import org.junit.Test;

public class UTF8IoUtilsTest {
  
  @Test
  public void writerFromGzipFile() throws IOException {
    File f = File.createTempFile("coltest",".gz");
    
    try(BufferedWriter writer = UTF8IoUtils.writerFromGzipFile(f)) {
      String[] data = new String[] { "this", "is", "some",
          "data", "in", "a", "list" };
      for (String line : data) {
        writer.append(line);
        writer.newLine();
      }
    }
  
    BufferedReader br = UTF8IoUtils.readerFromGzipFile(f);
    
    String x = br.lines().collect(Collectors.joining("\n"));
    System.out.println(x);
    
    f.delete();
  }
}