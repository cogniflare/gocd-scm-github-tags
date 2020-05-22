package io.cogniflare.gocd.github;

import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.tw.go.plugin.GitHelper;
import com.tw.go.plugin.model.GitConfig;
import com.tw.go.plugin.model.ModifiedFile;
import com.tw.go.plugin.model.Revision;
import com.tw.go.plugin.util.ListUtil;
import com.tw.go.plugin.util.StringUtil;
import io.cogniflare.gocd.github.gitRemoteProvider.GitRemoteProvider;
import io.cogniflare.gocd.github.settings.scm.PluginConfigurationView;
import io.cogniflare.gocd.github.util.BranchFilter;
import io.cogniflare.gocd.github.util.GitFactory;
import io.cogniflare.gocd.github.util.GitFolderFactory;
import io.cogniflare.gocd.github.util.JSONUtils;
import in.ashwanthkumar.utils.collections.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static io.cogniflare.gocd.github.util.JSONUtils.fromJSON;
import static java.util.Arrays.asList;

@Extension
public class GitHubPRBuildPlugin implements GoPlugin {
    private static final Logger LOGGER = Logger.getLoggerFor(GitHubPRBuildPlugin.class);

    public static final String EXTENSION_NAME = "scm";
    private static final List<String> goSupportedVersions = asList("1.0");

    public static final String REQUEST_SCM_CONFIGURATION = "scm-configuration";
    public static final String REQUEST_SCM_VIEW = "scm-view";
    public static final String REQUEST_VALIDATE_SCM_CONFIGURATION = "validate-scm-configuration";
    public static final String REQUEST_CHECK_SCM_CONNECTION = "check-scm-connection";
    public static final String REQUEST_PLUGIN_CONFIGURATION = "go.plugin-settings.get-configuration";
    public static final String REQUEST_PLUGIN_VIEW = "go.plugin-settings.get-view";
    public static final String REQUEST_VALIDATE_PLUGIN_CONFIGURATION = "go.plugin-settings.validate-configuration";

    public static final String REQUEST_LATEST_REVISION = "latest-revision";
    public static final String REQUEST_LATEST_REVISIONS_SINCE = "latest-revisions-since";
    public static final String REQUEST_CHECKOUT = "checkout";

    public static final String BRANCH_TO_REVISION_MAP = "BRANCH_TO_REVISION_MAP";
    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int NOT_FOUND_RESPONSE_CODE = 404;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;

    private GitRemoteProvider gitRemoteProvider;
    private final GitFactory gitFactory;
    private final GitFolderFactory gitFolderFactory;
    private GoApplicationAccessor goApplicationAccessor;

    public GitHubPRBuildPlugin() {
        try {
            Properties properties = new Properties();
            properties.load(getClass().getResourceAsStream("/defaults.properties"));

            Class<?> providerClass = Class.forName(properties.getProperty("provider"));
            Constructor<?> constructor = providerClass.getConstructor();
            gitRemoteProvider = (GitRemoteProvider) constructor.newInstance();
            gitFactory = new GitFactory();
            gitFolderFactory = new GitFolderFactory();
        } catch (Exception e) {
            throw new RuntimeException("could not create provider", e);
        }
    }

    public GitHubPRBuildPlugin(GitRemoteProvider gitRemoteProvider, GitFactory gitFactory, GitFolderFactory gitFolderFactory, GoApplicationAccessor goApplicationAccessor) {
        this.gitRemoteProvider = gitRemoteProvider;
        this.gitFactory = gitFactory;
        this.gitFolderFactory = gitFolderFactory;
        this.goApplicationAccessor = goApplicationAccessor;
    }

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        this.goApplicationAccessor = goApplicationAccessor;
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        switch (goPluginApiRequest.requestName()) {
            case REQUEST_SCM_CONFIGURATION:
                return getPluginConfiguration(gitRemoteProvider.getScmConfigurationView());
            case REQUEST_SCM_VIEW:
                try {
                    return getPluginView(gitRemoteProvider, gitRemoteProvider.getScmConfigurationView());
                } catch (IOException e) {
                    String message = "Failed to find template: " + e.getMessage();
                    return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, message);
                }
            case REQUEST_PLUGIN_CONFIGURATION:
                return getPluginConfiguration(gitRemoteProvider.getGeneralConfigurationView());
            case REQUEST_PLUGIN_VIEW:
                try {
                    return getPluginView(gitRemoteProvider, gitRemoteProvider.getGeneralConfigurationView());
                } catch (IOException e) {
                    String message = "Failed to find template: " + e.getMessage();
                    return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, message);
                }
            case REQUEST_VALIDATE_PLUGIN_CONFIGURATION:
                return renderJSON(SUCCESS_RESPONSE_CODE, Collections.emptyList());
            case REQUEST_VALIDATE_SCM_CONFIGURATION:
                return handleSCMValidation(goPluginApiRequest);
            case REQUEST_CHECK_SCM_CONNECTION:
                return handleSCMCheckConnection(goPluginApiRequest);
            case REQUEST_LATEST_REVISION:
                return handleGetLatestRevision(goPluginApiRequest);
            case REQUEST_LATEST_REVISIONS_SINCE:
                return handleLatestRevisionSince(goPluginApiRequest);
            case REQUEST_CHECKOUT:
                return handleCheckout(goPluginApiRequest);
        }
        return renderJSON(NOT_FOUND_RESPONSE_CODE, null);
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, goSupportedVersions);
    }

    void setGitRemoteProvider(GitRemoteProvider gitRemoteProvider) {
        this.gitRemoteProvider = gitRemoteProvider;
    }

    private GoPluginApiResponse getPluginView(GitRemoteProvider gitRemoteProvider, PluginConfigurationView view) throws IOException {
        if (view.hasConfigurationView()) {
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("displayValue", gitRemoteProvider.getName());
            response.put("template", getFileContents(view.templateName()));
            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } else {
            return renderJSON(NOT_FOUND_RESPONSE_CODE, null);
        }
    }

    private GoPluginApiResponse getPluginConfiguration(PluginConfigurationView view) {
        Map<String, Object> response = view.fields();
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMValidation(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> requestBodyMap = (Map<String, Object>) fromJSON(goPluginApiRequest.requestBody());
        final Map<String, String> configuration = keyValuePairs(requestBodyMap, "scm-configuration");
        final GitConfig gitConfig = getGitConfig(configuration);

        List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
        validate(response, (fieldValidation) -> validateUrl(gitConfig, fieldValidation));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMCheckConnection(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> requestBodyMap = (Map<String, Object>) fromJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(requestBodyMap, "scm-configuration");
        GitConfig gitConfig = getGitConfig(configuration);

        Map<String, Object> response = new HashMap<String, Object>();
        List<String> messages = new ArrayList<String>();

        checkConnection(gitConfig, response, messages);

        if (response.get("status") == null) {
            response.put("status", "success");
            messages.add("Could connect to URL successfully");
        }
        response.put("messages", messages);
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    GoPluginApiResponse handleGetLatestRevision(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> requestBodyMap = (Map<String, Object>) fromJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(requestBodyMap, "scm-configuration");
        GitConfig gitConfig = getGitConfig(configuration);
        String flyweightFolder = (String) requestBodyMap.get("flyweight-folder");
        LOGGER.info(String.format("Flyweight: %s", flyweightFolder));

        try {
            GitHelper git = gitFactory.create(gitConfig, gitFolderFactory.create(flyweightFolder));
            git.cloneOrFetch(gitRemoteProvider.getRefSpec());
            String tag = gitRemoteProvider.getLatestRelease(gitConfig, git);
            git.resetHard(tag);
            Revision revision = git.getLatestRevision();
            git.submoduleUpdate();

            Map<String, Object> response = new HashMap<String, Object>();
            Map<String, Object> revisionMap = getRevisionMap(gitConfig, revision, tag);
            response.put("revision", revisionMap);
            Map<String, String> scmDataMap = new HashMap<String, String>();
            response.put("scm-data", scmDataMap);

            // TODO pull from revisionMap
            String tag = "1.2.3";
            LOGGER.info(String.format("Triggered build for %s with head at %s", tag, revision.getRevision()));
            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } catch (Throwable t) {
            LOGGER.warn("get latest revision: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, removeUsernameAndPassword(t.getMessage(), gitConfig));
        }
    }

    private String removeUsernameAndPassword(String message, GitConfig gitConfig) {
        String messageForDisplay = message;
        String password = gitConfig.getPassword();
        if (StringUtils.isNotBlank(password)) {
            messageForDisplay = message.replaceAll(password, "****");
        }
        String username = gitConfig.getUsername();
        if (StringUtils.isNotBlank(username)) {
            messageForDisplay = messageForDisplay.replaceAll(username, "****");
        }
        return messageForDisplay;
    }

    GoPluginApiResponse handleLatestRevisionSince(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> requestBodyMap = (Map<String, Object>) fromJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(requestBodyMap, "scm-configuration");
        final GitConfig gitConfig = getGitConfig(configuration);
        Map<String, String> scmData = (Map<String, String>) requestBodyMap.get("scm-data");
        String flyweightFolder = (String) requestBodyMap.get("flyweight-folder");
        LOGGER.debug(String.format("Fetching latest for: %s", gitConfig.getUrl()));

        try {
            GitHelper git = gitFactory.create(gitConfig, gitFolderFactory.create(flyweightFolder));
            git.cloneOrFetch(gitRemoteProvider.getRefSpec());
            git.submoduleUpdate();

            Map<String, String> newerRevisions = new HashMap<String, String>();

            // TODO tag filter
            BranchFilter branchFilter = gitRemoteProvider
                    .getScmConfigurationView()
                    .getBranchFilter(configuration);

            LOGGER.info(String.format("new commits: %d", newerRevisions.size()));

            List<Map<String, Object>> revisions = new ArrayList<>();
            for (final String branch : newerRevisions.keySet()) {
                String lastKnownSHA = "abc";
                String latestSHA = newerRevisions.get(branch);
                if (StringUtils.isNotEmpty(lastKnownSHA)) {
                    git.resetHard(latestSHA);
                    List<Revision> allRevisionsSince;
                    try {
                        allRevisionsSince = git.getRevisionsSince(lastKnownSHA);
                    } catch (Exception e) {
                        allRevisionsSince = Collections.singletonList(git.getLatestRevision());
                    }
                    List<Map<String, Object>> changesSinceLastCommit = Lists.map(
                            allRevisionsSince,
                            revision -> getRevisionMap(gitConfig, revision, tag)
                    );
                    revisions.addAll(changesSinceLastCommit);
                } else {
                    Revision revision = git.getDetailsForRevision(latestSHA);
                    Map<String, Object> revisionMap = getRevisionMapForSHA(gitConfig, branch, revision);
                    revisions.add(revisionMap);
                }
            }
            Map<String, Object> response = new HashMap<>();
            response.put("revisions", revisions);
            Map<String, String> scmDataMap = new HashMap<>();
            response.put("scm-data", scmDataMap);
            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } catch (Throwable t) {
            LOGGER.warn("get latest revisions since: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, removeUsernameAndPassword(t.getMessage(), gitConfig));
        }
    }

    private Map<String, Object> getRevisionMapForSHA(GitConfig gitConfig, String branch, Revision revision) {
        // patch for building merge commits
        if (revision.isMergeCommit() && ListUtil.isEmpty(revision.getModifiedFiles())) {
            revision.setModifiedFiles(Lists.of(new ModifiedFile("/dev/null", "deleted")));
        }

        return getRevisionMap(gitConfig, revision, tag);
    }

    private boolean branchHasNewChange(String previousSHA, String latestSHA) {
        return previousSHA == null || !previousSHA.equals(latestSHA);
    }

    private GoPluginApiResponse handleCheckout(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> requestBodyMap = (Map<String, Object>) fromJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(requestBodyMap, "scm-configuration");
        GitConfig gitConfig = getGitConfig(configuration);
        String destinationFolder = (String) requestBodyMap.get("destination-folder");
        Map<String, Object> revisionMap = (Map<String, Object>) requestBodyMap.get("revision");
        String revision = (String) revisionMap.get("revision");
        LOGGER.info(String.format("destination: %s. commit: %s", destinationFolder, revision));

        try {
            GitHelper git = gitFactory.create(gitConfig, gitFolderFactory.create(destinationFolder));
            git.cloneOrFetch(gitRemoteProvider.getRefSpec());
            git.resetHard(revision);
            git.submoduleUpdate();

            Map<String, Object> response = new HashMap<String, Object>();
            response.put("status", "success");
            response.put("messages", Arrays.asList(String.format("Checked out to revision %s", revision)));

            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } catch (Throwable t) {
            LOGGER.warn("checkout: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, t.getMessage());
        }
    }

    GitConfig getGitConfig(Map<String, String> configuration) {
        GitConfig gitConfig = new GitConfig(
                configuration.get("url"),
                configuration.get("username"),
                configuration.get("password"),
                StringUtils.trimToNull(configuration.get("defaultBranch")),
                true,
                Boolean.parseBoolean(configuration.get("shallowClone"))
        );
        gitRemoteProvider.addConfigData(gitConfig);
        return gitConfig;
    }

    private void validate(List<Map<String, Object>> response, FieldValidator fieldValidator) {
        Map<String, Object> fieldValidation = new HashMap<String, Object>();
        fieldValidator.validate(fieldValidation);
        if (!fieldValidation.isEmpty()) {
            response.add(fieldValidation);
        }
    }

    Map<String, Object> getRevisionMap(GitConfig gitConfig, Revision revision, String tag) {
        List<Map<String, String>> modifiedFilesMapList = new ArrayList<>();
        if (!ListUtil.isEmpty(revision.getModifiedFiles())) {
            for (ModifiedFile modifiedFile : revision.getModifiedFiles()) {
                Map<String, String> modifiedFileMap = new HashMap<String, String>();
                modifiedFileMap.put("fileName", modifiedFile.getFileName());
                modifiedFileMap.put("action", modifiedFile.getAction());
                modifiedFilesMapList.add(modifiedFileMap);
            }
        }

        Map<String, String> customDataBag = new HashMap<String, String>();
        customDataBag.put("RELEASE_TAG", tag);
        gitRemoteProvider.populateRevisionData(gitConfig, revision,tag, customDataBag);

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("revision", revision.getRevision());
        response.put("user", revision.getUser());
        response.put("timestamp", new SimpleDateFormat(DATE_PATTERN).format(revision.getTimestamp()));
        response.put("revisionComment", revision.getComment());
        response.put("modifiedFiles", modifiedFilesMapList);
        response.put("data", customDataBag);
        return response;
    }

    private Map<String, String> keyValuePairs(Map<String, Object> requestBodyMap, String mainKey) {
        Map<String, String> keyValuePairs = new HashMap<String, String>();
        Map<String, Object> fieldsMap = (Map<String, Object>) requestBodyMap.get(mainKey);
        for (String field : fieldsMap.keySet()) {
            Map<String, Object> fieldProperties = (Map<String, Object>) fieldsMap.get(field);
            String value = (String) fieldProperties.get("value");
            keyValuePairs.put(field, value);
        }
        return keyValuePairs;
    }

    public void validateUrl(GitConfig gitConfig, Map<String, Object> fieldMap) {
        if (StringUtil.isEmpty(gitConfig.getUrl())) {
            fieldMap.put("key", "url");
            fieldMap.put("message", "URL is a required field");
        } else if (!gitRemoteProvider.isValidURL(gitConfig.getUrl())) {
            fieldMap.put("key", "url");
            fieldMap.put("message", "Invalid URL");
        }
    }

    public void checkConnection(GitConfig gitConfig, Map<String, Object> response, List<String> messages) {
        if (StringUtil.isEmpty(gitConfig.getUrl())) {
            response.put("status", "failure");
            messages.add("URL is empty");
        } else if (!gitRemoteProvider.isValidURL(gitConfig.getUrl())) {
            response.put("status", "failure");
            messages.add("Invalid URL");
        } else {
            try {
                gitRemoteProvider.checkConnection(gitConfig);
            } catch (Exception e) {
                response.put("status", "failure");
                messages.add(e.getMessage());
            }
        }
    }

    private String getFileContents(String filePath) throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream(filePath), StandardCharsets.UTF_8);
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : JSONUtils.toJSON(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }

}
