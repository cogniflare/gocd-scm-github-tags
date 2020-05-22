package io.cogniflare.gocd.github.provider.git;

import io.cogniflare.gocd.github.provider.AbstractProviderTest;
import io.cogniflare.gocd.github.provider.GitRemoteProvider;
import io.cogniflare.gocd.github.settings.scm.PluginConfigurationView;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class GitGitRemoteProviderTest extends AbstractProviderTest {

    @Test
    public void shouldReturnCorrectScmSettingsTemplate() throws Exception {
        PluginConfigurationView scmConfigurationView = getScmView();

        assertThat(scmConfigurationView.templateName(), is("/views/scm.template.branch.filter.html"));;
    }

    @Test
    public void shouldReturnCorrectScmSettingsFields() throws Exception {
        PluginConfigurationView scmConfigurationView = getScmView();

        assertThat(scmConfigurationView.fields().keySet(),
                   hasItems("url", "username", "password", "branchwhitelist", "branchblacklist", "defaultBranch", "shallowClone")
        );
        assertThat(scmConfigurationView.fields().size(), is(7));
    }

    @Test
    public void shouldReturnCorrectGeneralSettingsTemplate() throws Exception {
        PluginConfigurationView generalConfigurationView = getGeneralView();

        assertThat(generalConfigurationView.templateName(), is(""));
        assertThat(generalConfigurationView.hasConfigurationView(), is(false));
    }

    @Override
    protected GitRemoteProvider getProvider() {
        return new GitGitRemoteProvider();
    }
}