package io.cogniflare.gocd.github.gitRemoteProvider;

import io.cogniflare.gocd.github.settings.scm.PluginConfigurationView;


public abstract class AbstractProviderTest {

    protected abstract GitRemoteProvider getProvider();

    protected PluginConfigurationView getScmView() {
        return getProvider().getScmConfigurationView();
    }

    protected PluginConfigurationView getGeneralView() {
        return getProvider().getGeneralConfigurationView();
    }
}
