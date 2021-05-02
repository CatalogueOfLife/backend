package life.catalogue.importer.proxy;

import life.catalogue.api.vocab.DataFormat;
import org.gbif.dwc.terms.Term;

import java.util.List;

public class ArchiveDescriptor {
  
  public DataFormat format;
  public List<FileDescriptor> files;
  
  public static class FileDescriptor {
    public String name;
    public String url;
    public String encoding = "UTF-8";
    public String delimiter;
    public String quotation;
    public List<Term> header;
  }

  
}
