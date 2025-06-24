package org.graalvm.python.javainterfacegen.configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class YamlStringConfigurationLoader implements ConfigurationLoader {

    private final String text;

    public YamlStringConfigurationLoader(String text) {
        this.text = text;
    }

    @Override
    public Map<String, Object> loadConfiguration() throws Exception {
        Map<String, Object> defaultConfig = new DefaultConfigurationLoader().loadConfiguration();
        Map<String, Object> yamlConfig = null;

        try (InputStream inputStream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
            Yaml yaml = new Yaml();
            yamlConfig = yaml.load(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Configuration.merge(defaultConfig, yamlConfig);
    }
}
