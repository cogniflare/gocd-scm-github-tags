package io.cogniflare.gocd.github.provider;

import io.cogniflare.gocd.github.settings.scm.PluginConfigurationView;


public abstract class AbstractProviderTest {

    protected abstract Provider getProvider();

    protected PluginConfigurationView getScmView() {
        return getProvider().getScmConfigurationView();
    }

    protected PluginConfigurationView getGeneralView() {
        return getProvider().getGeneralConfigurationView();
    }
}
