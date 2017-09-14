package org.col.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;

import java.io.IOException;
import java.io.InputStream;

public class ConfigTestUtils {
    private static ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    public static PgConfig testConfig() {
        try {
            InputStream cfgStream = Resources.getResource("test-config.yml").openStream();
            return MAPPER.readValue(cfgStream, PgConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
