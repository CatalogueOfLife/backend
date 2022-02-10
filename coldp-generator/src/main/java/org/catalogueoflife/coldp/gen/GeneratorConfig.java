package org.catalogueoflife.coldp.gen;

import java.io.File;
import java.util.List;

import javax.validation.constraints.NotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Joiner;

/**
 *
 */
public class GeneratorConfig {

  @Parameter(names = {"-r", "--repository"})
  @NotNull
  public File repository = new File("/tmp/coldp-generator");

  @Parameter(names = {"-s", "--source"}, required = true)
  @NotNull
  public String source;

  /**
   * Returns the directory with the decompressed archive folder created by the checklist builder
   */
  public File archiveDir() {
    return new File(repository, source);
  }

  public Class<? extends AbstractGenerator> builderClass() {
    try {
      String classname = GeneratorConfig.class.getPackage().getName() + "." + source.toLowerCase() + ".Generator";
      return (Class<? extends AbstractGenerator>) GeneratorConfig.class.getClassLoader().loadClass(classname);

    } catch (ClassNotFoundException e) {
      List<String> sources = Lists.newArrayList();
      Joiner joiner = Joiner.on(",").skipNulls();
      throw new IllegalArgumentException(source + " not a valid source. Please use one of: " + joiner.join(sources), e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
