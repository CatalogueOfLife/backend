package org.col.importer.proxy;

import java.util.List;

import org.col.api.vocab.DataFormat;
import org.gbif.dwc.terms.Term;

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
