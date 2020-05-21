package io.cogniflare.gocd.github.settings.scm;

import io.cogniflare.gocd.github.util.BranchFilter;

import java.util.Map;

public interface ScmPluginConfigurationView extends PluginConfigurationView {

    BranchFilter getBranchFilter(Map<String, String> configuration);
}
