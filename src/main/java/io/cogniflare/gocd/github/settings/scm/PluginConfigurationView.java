package io.cogniflare.gocd.github.settings.scm;

import java.util.Map;

public interface PluginConfigurationView {
    String templateName();

    Map<String, Object> fields();

    boolean hasConfigurationView();
}
