package io.cogniflare.gocd.github.gitRemoteProvider.github;

import com.tw.go.plugin.GitHelper;
import com.tw.go.plugin.model.GitConfig;
import com.tw.go.plugin.model.Revision;
import com.tw.go.plugin.util.StringUtil;
import in.ashwanthkumar.utils.lang.StringUtils;
import io.cogniflare.gocd.github.gitRemoteProvider.GitRemoteProvider;
import io.cogniflare.gocd.github.settings.general.DefaultGeneralPluginConfigurationView;
import io.cogniflare.gocd.github.settings.general.GeneralPluginConfigurationView;
import io.cogniflare.gocd.github.settings.scm.DefaultScmPluginConfigurationView;
import io.cogniflare.gocd.github.settings.scm.ScmPluginConfigurationView;
import io.cogniflare.gocd.github.util.URLUtils;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class GitHubGitRemoteProvider implements GitRemoteProvider {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubGitRemoteProvider.class);
    public static final String REF_SPEC = "refs/tags/*:refs/tags/*";

    @Override
    public String getName() {
        return "Github";
    }

    @Override
    public void addConfigData(GitConfig gitConfig) {
        try {
            Properties props = GHUtils.readPropertyFile();
            if (StringUtil.isEmpty(gitConfig.getUsername())) {
                gitConfig.setUsername(props.getProperty("login"));
            }
            if (StringUtil.isEmpty(gitConfig.getPassword())) {
                gitConfig.setPassword(props.getProperty("password"));
            }
        } catch (IOException e) {
            // ignore
        }
    }

    @Override
    public boolean isInvalidURL(String url) {
        return !new URLUtils().isValidURL(url);
    }

    @Override
    public void checkConnection(GitConfig gitConfig) {
        try {
            loginWith(gitConfig).getRepository(GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl()));
        } catch (Exception e) {
            throw new RuntimeException(String.format("check connection failed. %s", e.getMessage()), e);
        }
    }

    private static String getDomainName(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String domain = uri.getHost();
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    @Override
    public String getRefSpec() {
        return REF_SPEC;
    }

    @Override
    public void populateReleaseData(GitConfig gitConfig, Revision prSHA, String tag, Map<String, String> data) {

        boolean isDisabled = System.getProperty("go.plugin.github.pr.populate-details", "Y").equals("N");
        if (isDisabled) {
            LOG.debug("Populating PR details is disabled");
            return;
        }

        try {
            Optional<GHRelease> release = getGithubReleaseForTag(gitConfig, tag);

            if (!release.isPresent()) {
                LOG.error(String.format("Cannot find release: %s", tag));
                return;
            }

            data.put("RELEASE_NAME", String.valueOf(release.get().getName()));
            data.put("RELEASE_BODY", String.valueOf(release.get().getBody()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Optional<GHRelease> getGithubReleaseForTag(GitConfig gitConfig, String tag) throws IOException {
        return loginWith(gitConfig)
                .getRepository(GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl()))
                .listReleases()
                .asList()
                .stream()
                .filter(rel -> Objects.equals(rel.getTagName(), tag))
                .findAny();
    }

    @Override
    public ScmPluginConfigurationView getScmConfigurationView() {
        return new DefaultScmPluginConfigurationView();
    }

    @Override
    public GeneralPluginConfigurationView getGeneralConfigurationView() {
        return new DefaultGeneralPluginConfigurationView();
    }

    @Override
    public String getLatestRelease(GitConfig gitConfig, GitHelper git) throws IOException {
        PagedIterable<GHRelease> releases = loginWith(gitConfig)
                .getRepository(GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl()))
                .listReleases();

        // assumes github api order to present latest release first
        GHRelease latestRelease = releases.iterator().next();
        return latestRelease.getTagName();
    }

    private GitHub loginWith(GitConfig gitConfig) throws IOException {
        if (!hasCredentials(gitConfig)) {
            return GitHub.connect();
        }

        // Cloud auth methods
        try {
            return GitHub.connectUsingPassword(gitConfig.getUsername(), gitConfig.getPassword());
        } catch (Exception t) {
            LOG.trace("Cannot authenticate to GitHub cloud using password", t);
        }
        try {
            return GitHub.connect(gitConfig.getUsername(), gitConfig.getPassword());
        } catch (Exception t) {
            LOG.trace("Cannot authenticate to GitHub cloud using oAuth", t);
        }

        // Enterprise auth methods
        try {
            String url = gitConfig.getUrl();
            String domain = getDomainName(url);
            String apiEndpoint = String.format("https://%s/api/v3/", domain);

            try {
                return GitHub.connectToEnterprise(apiEndpoint, gitConfig.getUsername(), gitConfig.getPassword());
            } catch (Exception t) {
                LOG.trace(String.format("Cannot authenticate to GitHub enterprise (%s) using password", apiEndpoint), t);
            }
            try {
                return GitHub.connectToEnterprise(apiEndpoint, gitConfig.getPassword());
            } catch (Exception t) {
                LOG.trace(String.format("Cannot authenticate to GitHub enterprise (%s) using oAuth", apiEndpoint), t);
            }

        } catch (URISyntaxException e) {
            LOG.trace("Cannot get repository domain name", e);
        }

        throw new IOException("Cannot authenticate to github repository.");
    }

    private boolean hasCredentials(GitConfig gitConfig) {
        return StringUtils.isNotEmpty(gitConfig.getUsername()) && StringUtils.isNotEmpty(gitConfig.getPassword());
    }
}
