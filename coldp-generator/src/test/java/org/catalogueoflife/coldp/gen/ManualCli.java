package org.catalogueoflife.coldp.gen;

import org.junit.Ignore;

@Ignore
public class ManualCli {

  public static void main(String[] args) throws Exception {
    GeneratorCLI.main( new String[]{"-s", "wcvp", "-r", "/tmp/coldp/archives"} );
  }
}