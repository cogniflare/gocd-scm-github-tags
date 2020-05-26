package io.cogniflare.gocd.github.gitRemoteProvider;

import com.tw.go.plugin.GitHelper;
import com.tw.go.plugin.model.GitConfig;
import com.tw.go.plugin.model.Revision;
import io.cogniflare.gocd.github.settings.general.GeneralPluginConfigurationView;
import io.cogniflare.gocd.github.settings.scm.ScmPluginConfigurationView;

import java.io.IOException;
import java.util.Map;

public interface GitRemoteProvider {
    String getName();

    void addConfigData(GitConfig gitConfig);

    boolean isInvalidURL(String url);

    void checkConnection(GitConfig gitConfig);

    String getRefSpec();

    void populateReleaseData(GitConfig gitConfig, Revision prSHA, String tag, Map<String, String> data);

    ScmPluginConfigurationView getScmConfigurationView();

    GeneralPluginConfigurationView getGeneralConfigurationView();

    String getLatestRelease(GitConfig gitConfig, GitHelper git) throws IOException;
}
