package life.catalogue.dw.jersey;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;

public class FileStreamingOutput implements StreamingOutput {

  private File file;

  public FileStreamingOutput(File file) {
    this.file = file;
  }

  @Override
  public void write(OutputStream out) throws WebApplicationException {
    try (FileInputStream in = new FileInputStream(file)){
      IOUtils.copy(in, out);
      out.flush();
    } catch (Exception e) {
      throw new WebApplicationException(e);
    }
  }

}