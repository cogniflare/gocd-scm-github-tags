package io.cogniflare.gocd.github.gitRemoteProvider.github;

import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.tw.go.plugin.GitHelper;
import com.tw.go.plugin.model.GitConfig;
import com.tw.go.plugin.model.Revision;
import com.tw.go.plugin.util.StringUtil;
import io.cogniflare.gocd.github.gitRemoteProvider.GitRemoteProvider;
import io.cogniflare.gocd.github.settings.general.DefaultGeneralPluginConfigurationView;
import io.cogniflare.gocd.github.settings.general.GeneralPluginConfigurationView;
import io.cogniflare.gocd.github.settings.scm.DefaultScmPluginConfigurationView;
import io.cogniflare.gocd.github.settings.scm.ScmPluginConfigurationView;
import io.cogniflare.gocd.github.util.URLUtils;
import in.ashwanthkumar.utils.func.Function;
import in.ashwanthkumar.utils.lang.StringUtils;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class GitHubGitRemoteProvider implements GitRemoteProvider {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubGitRemoteProvider.class);
    // public static final String PR_FETCH_REFSPEC = "+refs/pull/*/merge:refs/gh-merge/remotes/origin/*";
    // public static final String PR_MERGE_PREFIX = "refs/gh-merge/remotes/origin/";
    public static final String REF_SPEC = "+refs/pull/*/head:refs/remotes/origin/pull-request/*";
    public static final String REF_PATTERN = "refs/remotes/origin/pull-request/";
    public static final String PUBLIC_GITHUB_ENDPOINT = "https://api.github.com";

    @Override
    public GoPluginIdentifier getPluginId() {
        return new GoPluginIdentifier("github.pr", Arrays.asList("1.0"));
    }

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
    public boolean isValidURL(String url) {
        return new URLUtils().isValidURL(url);
    }

    @Override
    public void checkConnection(GitConfig gitConfig) {
        try {
            loginWith(gitConfig).getRepository(GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl()));
        } catch (Exception e) {
            throw new RuntimeException(String.format("check connection failed. %s", e.getMessage()), e);
        }
    }

    @Override
    public String getRefSpec() {
        return REF_SPEC;
    }

    @Override
    public String getRefPattern() {
        return REF_PATTERN;
    }

    @Override
    public void populateRevisionData(GitConfig gitConfig, Revision prSHA, String tag, Map<String, String> data) {

        boolean isDisabled = System.getProperty("go.plugin.github.pr.populate-details", "Y").equals("N");
        if (isDisabled) {
            LOG.debug("Populating PR details is disabled");
            return;
        }

        try {
            Optional<GHRelease> release = getGithubReleaseForTag(gitConfig, tag);

            if (!release.isPresent()) {
                // TODO log error
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
    public String getLatestRelease(GitConfig gitConfig, GitHelper git) {
        try {
            PagedIterable<GHRelease> releases = loginWith(gitConfig)
                    .getRepository(GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl()))
                    .listReleases();
            GHRelease latestRelease = releases.iterator().next();
            String tag = latestRelease.getTagName();
            return tag;
        } catch (IOException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }

    private PullRequestStatus getPullRequestStatus(GitConfig gitConfig, String prId, String prSHA) {
        try {
            GHPullRequest currentPR = pullRequestFrom(gitConfig, Integer.parseInt(prId));
            return transformGHPullRequestToPullRequestStatus(prSHA).apply(currentPR);
        } catch (Exception e) {
            // ignore
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }

    private GHPullRequest pullRequestFrom(GitConfig gitConfig, int currentPullRequestID) throws IOException {
        return loginWith(gitConfig)
                .getRepository(GHUtils.parseGithubUrl(gitConfig.getEffectiveUrl()))
                .getPullRequest(currentPullRequestID);
    }

    private Function<GHPullRequest, PullRequestStatus> transformGHPullRequestToPullRequestStatus(final String mergedSHA) {
        return input -> {
            int prID = GHUtils.prIdFrom(input.getDiffUrl().toString());
            try {
                GHUser user = input.getUser();
                return new PullRequestStatus(prID, input.getHead().getSha(), mergedSHA, input.getHead().getLabel(),
                        input.getBase().getLabel(), input.getHtmlUrl().toString(), user.getName(),
                        user.getEmail(), input.getBody(), input.getTitle());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private GitHub loginWith(GitConfig gitConfig) throws IOException {
        if (hasCredentials(gitConfig))
            return GitHub.connectUsingPassword(gitConfig.getUsername(), gitConfig.getPassword());
        else return GitHub.connect();
    }

    private boolean hasCredentials(GitConfig gitConfig) {
        return StringUtils.isNotEmpty(gitConfig.getUsername()) && StringUtils.isNotEmpty(gitConfig.getPassword());
    }
}
