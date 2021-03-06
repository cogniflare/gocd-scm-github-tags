package io.cogniflare.gocd.github.gitRemoteProvider.github;

import io.cogniflare.gocd.github.gitRemoteProvider.AbstractProviderTest;
import io.cogniflare.gocd.github.gitRemoteProvider.GitRemoteProvider;
import io.cogniflare.gocd.github.settings.scm.PluginConfigurationView;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class GitHubGitRemoteProviderTest extends AbstractProviderTest {

    @Test
    public void shouldReturnCorrectScmSettingsTemplate() {
        PluginConfigurationView scmConfigurationView = getScmView();

        assertThat(scmConfigurationView.templateName(), is("/views/scm.template.html"));
    }

    @Test
    public void shouldReturnCorrectScmSettingsFields() {
        PluginConfigurationView scmConfigurationView = getScmView();

        assertThat(scmConfigurationView.fields().keySet(),
                hasItems("url", "username", "password", "defaultBranch", "shallowClone")
        );
        assertThat(scmConfigurationView.fields().size(), is(5));
    }

    @Test
    public void shouldReturnCorrectGeneralSettingsTemplate() {
        PluginConfigurationView generalConfigurationView = getGeneralView();

        assertThat(generalConfigurationView.templateName(), is(""));
        assertThat(generalConfigurationView.hasConfigurationView(), is(false));
    }

    @Override
    protected GitRemoteProvider getProvider() {
        return new GitHubGitRemoteProvider();
    }

}