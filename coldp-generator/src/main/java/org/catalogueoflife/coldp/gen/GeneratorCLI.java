package org.catalogueoflife.coldp.gen;

import java.lang.reflect.Constructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;

/**
 * Checklist build command
 */
public class GeneratorCLI {
    private static final Logger LOG = LoggerFactory.getLogger(GeneratorCLI.class);

    public static void main(String[] args) throws Exception {
        GeneratorConfig cfg = new GeneratorConfig();
        new JCommander(cfg, args);

        LOG.info("Building {} archive", cfg.source);
        Class<? extends AbstractGenerator> abClass = cfg.builderClass();
        Constructor<? extends AbstractGenerator> cons = abClass.getConstructor(GeneratorConfig.class);
        AbstractGenerator builder = cons.newInstance(cfg);
        builder.run();
        LOG.info("{} archive completed", cfg.source);
    }

}
