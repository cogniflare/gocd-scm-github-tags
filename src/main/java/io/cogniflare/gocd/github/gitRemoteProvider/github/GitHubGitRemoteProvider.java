package io.cogniflare.gocd.github.gitRemoteProvider.github;

import com.thoughtworks.go.plugin.api.logging.Logger;
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class GitHubGitRemoteProvider implements GitRemoteProvider {
    private static final Logger LOGGER = Logger.getLoggerFor(GitHubGitRemoteProvider.class);
    private static final String REF_SPEC = "refs/tags/*:refs/tags/*";
    private GitHub github;

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

            String repository = GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl());
            LOGGER.info(String.format("checking connection to repository: %s", repository));

            loginWith(gitConfig)
                    .getRepository(repository);
        } catch (Exception e) {
            String message = String.format("check connection failed. %s", e.getMessage());
            LOGGER.info(message);
            throw new RuntimeException(message, e);
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
            LOGGER.debug("Populating PR details is disabled");
            return;
        }

        try {
            Optional<GHRelease> release = getGithubReleaseForTag(gitConfig, tag);

            if (!release.isPresent()) {
                LOGGER.error(String.format("Cannot find release: %s", tag));
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
        if (github == null) {
            github = _loginWith(gitConfig);
        }
        return github;
    }

    private GitHub _loginWith(GitConfig gitConfig) throws IOException {
        LOGGER.debug("Login to github, env:");
        LOGGER.debug(String.format("https.proxyHost: %s", System.getProperty("https.proxyHost")));
        LOGGER.debug(String.format("https.proxyPort: %s", System.getProperty("https.proxyPort")));
        LOGGER.debug(String.format("http.nonProxyHosts: %s", System.getProperty("http.nonProxyHosts")));

        if (!hasCredentials(gitConfig)) {
            LOGGER.info("Github credentials not provided, using config file");
            return GitHub.connect();
        }

        String repositoryId = GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl());

        // Enterprise auth methods
        try {
            String url = gitConfig.getUrl();
            String domain = getDomainName(url);
            String apiEndpoint = String.format("https://%s/api/v3/", domain);

            if (!Objects.equals(domain, "github.com")) {
                try {
                    GitHub gitHub = GitHub.connectToEnterprise(apiEndpoint, gitConfig.getUsername(), gitConfig.getPassword());
                    gitHub.getRepository(repositoryId); // test connection
                    LOGGER.info("Successfully authenticated to GitHub enterprise using password");
                    return gitHub;
                } catch (Exception t) {
                    LOGGER.error(String.format("Cannot authenticate to GitHub enterprise (%s) using password", apiEndpoint), t);
                }
                try {
                    GitHub gitHub = GitHub.connectToEnterprise(apiEndpoint, gitConfig.getPassword());
                    gitHub.getRepository(repositoryId); // test connection
                    LOGGER.info("Successfully authenticated to GitHub enterprise using oAuth");
                    return gitHub;
                } catch (Exception t) {
                    LOGGER.error(String.format("Cannot authenticate to GitHub enterprise (%s) using oAuth", apiEndpoint), t);
                }
            }
        } catch (URISyntaxException e) {
            LOGGER.error("Cannot get repository domain name", e);
        }

        // Cloud auth methods
        try {
            GitHub gitHub = GitHub.connectUsingPassword(gitConfig.getUsername(), gitConfig.getPassword());
            gitHub.getRepository(repositoryId); // test connection
            LOGGER.info("Successfully authenticated to GitHub cloud using password");
            return gitHub;
        } catch (Exception t) {
            LOGGER.error("Cannot authenticate to GitHub cloud using password", t);
        }
        try {
            GitHub gitHub = GitHub.connect(gitConfig.getUsername(), gitConfig.getPassword());
            gitHub.getRepository(repositoryId); // test connection
            LOGGER.info("Successfully authenticated to GitHub cloud using oAuth");
            return gitHub;
        } catch (Exception t) {
            LOGGER.error("Cannot authenticate to GitHub cloud using oAuth", t);
        }

        throw new IOException("Cannot authenticate to github repository.");
    }

    private boolean hasCredentials(GitConfig gitConfig) {
        return StringUtils.isNotEmpty(gitConfig.getUsername()) && StringUtils.isNotEmpty(gitConfig.getPassword());
    }
}
