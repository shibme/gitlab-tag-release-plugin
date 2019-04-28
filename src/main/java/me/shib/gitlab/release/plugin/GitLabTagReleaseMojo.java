package me.shib.gitlab.release.plugin;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.util.FileUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabTag;
import org.gitlab.api.models.GitlabUpload;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Goal which attaches a file to a GitLab release
 *
 * @goal release
 * @phase deploy
 */
public class GitLabTagReleaseMojo extends AbstractMojo {

    private static final String defaultServerUrl = "https://gitlab.com";
    private static final String defaultBranch = "master";
    /**
     * @see <a href="https://maven.apache.org/scm/scm-url-format.html">SCM URL Format</a>
     */
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile(
            "^(scm:git[:|])?" +                                //Maven prefix for git SCM
                    "(https?://gitlab\\.com/|git@gitlab\\.com:)" +    //GitLab prefix for HTTP/HTTPS/SSH/Subversion scheme
                    "([^/]+/[^/]*?)" +                                //Repository ID
                    "(\\.git)?$"                                    //Optional suffix ".git"
            , Pattern.CASE_INSENSITIVE);
    /**
     * Server id for GitLab access.
     *
     * @parameter default-value="gitlab" property="gitlab"
     */
    private String serverId;
    /**
     * The name of the release
     *
     * @parameter default-value="https://gitlab.com"
     */
    private String serverUrl;
    /**
     * The tag name this release is based on.
     *
     * @parameter property="project.version"
     */
    private String tag;
    /**
     * The name of the release
     *
     * @parameter default-value="master"
     */
    private String gitBranch;
    /**
     * The tag release message
     *
     * @parameter property="project.name"
     */
    private String message;
    /**
     * The tag release description
     *
     * @parameter property="project.description"
     */
    private String description;
    /**
     * The GitLab id of the project. By default initialized from the project scm connection
     *
     * @parameter default-value="${project.scm.connection}" property="release.repositoryId"
     * @required
     */
    private String repositoryId;
    /**
     * The Maven settings
     *
     * @parameter property="settings"
     */
    private Settings settings;
    /**
     * The Maven session
     *
     * @parameter property="session"
     */
    private MavenSession session;
    /**
     * The file to upload to the release. Default is ${project.build.directory}/${project.artifactId}-${project.version}.${project.packaging} (the main artifact)
     *
     * @parameter
     */
    private String artifact;
    /**
     * A specific <code>fileSet</code> rule to select files and directories for upload to the release.
     *
     * @parameter
     */
    private FileSet fileSet;
    /**
     * A list of <code>fileSet</code> rules to select files and directories for upload to the release.
     *
     * @parameter
     */
    private List<FileSet> fileSets;
    /**
     * Flag to indicate to overwrite of the tag. Default is false
     *
     * @parameter default-value=false
     */
    private Boolean overwriteTag;
    @Requirement
    private PlexusContainer container;
    /**
     * If this is a prerelease. Will be set by default according to ${project.version} (see {@link #guessPreRelease(String)}.
     */
    private Boolean prerelease;
    /**
     * Fail plugin execution if tag release already exists.
     *
     * @parameter default-value=false
     */
    private Boolean failOnExistingTagRelease;

    public static String computeRepositoryId(String id) {
        Matcher matcher = REPOSITORY_PATTERN.matcher(id);
        if (matcher.matches()) {
            return matcher.group(3);
        } else {
            return id;
        }
    }

    /**
     * Guess if a version defined in POM should be considered as {@link #prerelease}.
     */
    static boolean guessPreRelease(String version) {
        return version.endsWith("-SNAPSHOT")
                || StringUtils.containsIgnoreCase(version, "-alpha")
                || StringUtils.containsIgnoreCase(version, "-beta")
                || StringUtils.containsIgnoreCase(version, "-RC")
                || StringUtils.containsIgnoreCase(version, ".RC")
                || StringUtils.containsIgnoreCase(version, ".M")
                || StringUtils.containsIgnoreCase(version, ".BUILD_SNAPSHOT");
    }

    public void execute() throws MojoExecutionException {
        if (serverUrl == null)
            serverUrl = defaultServerUrl;
        if (gitBranch == null)
            gitBranch = defaultBranch;
        if (prerelease == null)
            prerelease = guessPreRelease(tag);
        repositoryId = computeRepositoryId(repositoryId);
        GitlabAPI gitlabAPI;
        try {
            gitlabAPI = createGitlabAPI(serverId, serverUrl);
        } catch (IOException e) {
            getLog().error(e);
            throw new MojoExecutionException("Failed to connect with GitLab", e);
        }
        GitlabProject project;
        try {
            project = gitlabAPI.getProject(repositoryId);
        } catch (IOException e) {
            getLog().error(e);
            throw new MojoExecutionException("Failed to find project: " + repositoryId, e);
        }
        try {
            GitlabTag gitlabTag = findTag(gitlabAPI, project, tag);
            if (gitlabTag != null) {
                if (overwriteTag) {
                    getLog().warn("Deleting existing tag: " + tag);
                    gitlabAPI.deleteTag(project.getId(), tag);
                } else {
                    String message = "Tag release" + tag + " already exists. Not creating";
                    if (failOnExistingTagRelease) {
                        throw new MojoExecutionException(message);
                    }
                    getLog().info(message);
                    return;
                }
            }
            StringBuilder fileListBuilder = new StringBuilder();
            try {
                List<GitlabUpload> uploads = new ArrayList<GitlabUpload>();
                if (artifact != null && !artifact.trim().isEmpty()) {
                    File asset = new File(artifact);
                    if (asset.exists()) {
                        uploads.add(uploadAsset(gitlabAPI, project, asset));
                    }
                }
                if (fileSet != null) {
                    uploads.addAll(uploadAssets(gitlabAPI, project, fileSet));
                }
                if (fileSets != null) {
                    for (FileSet set : fileSets) {
                        uploads.addAll(uploadAssets(gitlabAPI, project, set));
                    }
                }
                if (uploads.size() > 0) {
                    fileListBuilder.append("### Artifacts");
                    for (GitlabUpload upload : uploads) {
                        StringBuilder sharableMarkdown = new StringBuilder();
                        sharableMarkdown.append('[').append(upload.getAlt()).append(']');
                        sharableMarkdown.append('(').append(project.getWebUrl()).append(upload.getUrl()).append(')');
                        fileListBuilder.append("\n* ").append(sharableMarkdown);
                    }
                }
            } catch (IOException e) {
                getLog().error(e);
                throw new MojoExecutionException("Failed to upload assets", e);
            }
            if (description == null) {
                description = fileListBuilder.toString();
            } else {
                description = description + "\n" + fileListBuilder.toString();
            }
            getLog().info("Creating tag: " + tag);
            gitlabAPI.addTag(project, tag, gitBranch, message, description);
        } catch (IOException e) {
            getLog().error(e);
            throw new MojoExecutionException("Failed to create tag-release", e);
        }
    }

    private GitlabUpload uploadAsset(GitlabAPI gitlabAPI, GitlabProject project, File asset) throws IOException {
        getLog().info("Processing asset " + asset.getPath());
        GitlabUpload upload = gitlabAPI.uploadFile(project, asset);
        getLog().info("Uploaded asset: " + asset.getPath());
        return upload;
    }

    private List<GitlabUpload> uploadAssets(GitlabAPI gitlabAPI, GitlabProject project, FileSet fileset) throws IOException {
        List<File> assets = FileUtils.getFiles(
                new File(fileset.getDirectory()),
                StringUtils.join(fileset.getIncludes(), ','),
                StringUtils.join(fileset.getExcludes(), ',')
        );
        List<GitlabUpload> uploads = new ArrayList<GitlabUpload>();
        for (File asset : assets) {
            uploads.add(uploadAsset(gitlabAPI, project, asset));
        }
        return uploads;
    }

    private GitlabTag findTag(GitlabAPI gitlabAPI, GitlabProject project, String releaseNameToFind) throws IOException {
        List<GitlabTag> tags = gitlabAPI.getTags(project);
        for (GitlabTag tag : tags) {
            if (releaseNameToFind.equals(tag.getName())) {
                return tag;
            }
        }
        return null;
    }

    private GitlabAPI getGitLabAPI(String serverUrl, String username, String password) throws IOException {
        String privateToken = GitlabAPI.connect(serverUrl, username, password).getPrivateToken();
        return GitlabAPI.connect(serverUrl, privateToken);
    }

    private GitlabAPI createGitlabAPI(String serverId, String serverUrl) throws MojoExecutionException, IOException {
        String usernameProperty = System.getProperty("username");
        String passwordProperty = System.getProperty("password");
        if (usernameProperty != null && passwordProperty != null) {
            getLog().debug("Using server credentials from system properties 'username' and 'password'");
            return getGitLabAPI(serverUrl, usernameProperty, passwordProperty);
        }

        Server server = getServer(settings, serverId);
        if (server == null)
            throw new MojoExecutionException(MessageFormat.format("Server ''{0}'' not found in settings", serverId));

        getLog().debug(MessageFormat.format("Using ''{0}'' server credentials", serverId));

        try {
            SettingsDecrypter settingsDecrypter = container.lookup(SettingsDecrypter.class);
            SettingsDecryptionResult result = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
            server = result.getServer();
        } catch (ComponentLookupException cle) {
            throw new MojoExecutionException("Unable to lookup SettingsDecrypter: " + cle.getMessage(), cle);
        }

        String serverUsername = server.getUsername();
        String serverPassword = server.getPassword();
        String serverAccessToken = server.getPrivateKey();
        if (StringUtils.isNotEmpty(serverUsername) && StringUtils.isNotEmpty(serverPassword))
            return getGitLabAPI(serverUrl, serverUsername, serverPassword);
        else if (StringUtils.isNotEmpty(serverAccessToken))
            return GitlabAPI.connect(serverUrl, serverAccessToken);
        else
            throw new MojoExecutionException("Configuration for server " + serverId + " has no login credentials");
    }

    /**
     * Get server with given id
     *
     * @param settings Maven setting
     * @param serverId must be non-null and non-empty
     * @return server or null if none matching
     */
    private Server getServer(final Settings settings, final String serverId) {
        if (settings == null)
            return null;
        List<Server> servers = settings.getServers();
        if (servers == null || servers.isEmpty())
            return null;

        for (Server server : servers)
            if (serverId.equals(server.getId()))
                return server;
        return null;
    }

    public void contextualize(Context context) throws ContextException {
        container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
    }
}
