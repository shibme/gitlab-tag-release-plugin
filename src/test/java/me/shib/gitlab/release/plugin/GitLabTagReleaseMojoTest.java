package me.shib.gitlab.release.plugin;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class GitLabTagReleaseMojoTest {

    private Map<String, String> computeRepositoryIdData;

    @Before
    public void setUp() throws Exception {
        computeRepositoryIdData = new HashMap<String, String>();

        computeRepositoryIdData.put("scm:git:https://gitlab.com/shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("scm:git|https://gitlab.com/shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("https://gitlab.com/shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");

        computeRepositoryIdData.put("scm:git:http://gitlab.com/shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("scm:git|http://gitlab.com/shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("http://gitlab.com/shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");

        computeRepositoryIdData.put("scm:git:git@gitlab.com:shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("scm:git|git@gitlab.com:shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("git@gitlab.com:shibme/gitlab-tag-release-plugin.git", "shibme/gitlab-tag-release-plugin");

        computeRepositoryIdData.put("scm:git:https://gitlab.com/shibme/gitlab-tag-release-plugin", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("scm:git|https://gitlab.com/shibme/gitlab-tag-release-plugin", "shibme/gitlab-tag-release-plugin");
        computeRepositoryIdData.put("https://gitlab.com/shibme/gitlab-tag-release-plugin", "shibme/gitlab-tag-release-plugin");
    }

    @Test
    public void testComputeRepositoryId() throws Exception {
        for (String source : computeRepositoryIdData.keySet()) {
            String expected = computeRepositoryIdData.get(source);
            assertEquals(source, expected, GitLabTagReleaseMojo.computeRepositoryId(source));
        }
    }

    @Test
    public void testGuessPreRelease() {
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-SNAPSHOT"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-alpha"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-alpha-1"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-beta"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-beta-1"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-RC"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-RC1"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-rc1"));
        assertTrue(GitLabTagReleaseMojo.guessPreRelease("1.0-rc-1"));

        assertFalse(GitLabTagReleaseMojo.guessPreRelease("1"));
        assertFalse(GitLabTagReleaseMojo.guessPreRelease("1.0"));
    }
}
