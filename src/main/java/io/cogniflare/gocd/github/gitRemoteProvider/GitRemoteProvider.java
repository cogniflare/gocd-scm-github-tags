package io.cogniflare.gocd.github.gitRemoteProvider;

import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.tw.go.plugin.GitHelper;
import com.tw.go.plugin.model.GitConfig;
import com.tw.go.plugin.model.Revision;
import io.cogniflare.gocd.github.settings.general.GeneralPluginConfigurationView;
import io.cogniflare.gocd.github.settings.scm.ScmPluginConfigurationView;

import java.util.Map;

public interface GitRemoteProvider {
    GoPluginIdentifier getPluginId();

    String getName();

    void addConfigData(GitConfig gitConfig);

    boolean isValidURL(String url);

    void checkConnection(GitConfig gitConfig);

    String getRefSpec();

    String getRefPattern();

    void populateRevisionData(GitConfig gitConfig, Revision prSHA, String tag, Map<String, String> data);

    ScmPluginConfigurationView getScmConfigurationView();

    GeneralPluginConfigurationView getGeneralConfigurationView();

    String getLatestRelease(GitConfig gitConfig, GitHelper git);
}
