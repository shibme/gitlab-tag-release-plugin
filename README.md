# GitLab Tag-Release Plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/me.shib.lib/gitlab-tag-release-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/me.shib.lib/gitlab-tag-release-plugin)

Maven plugin to create GitLab tag-releases and upload assets to the tag-release

Sample configuration for `pom.xml`

```
<plugin>
    <groupId>me.shib.lib</groupId>
    <artifactId>gitlab-tag-release-plugin</artifactId>
    <version>0.1.0</version>
    <configuration>
        <serverUrl>https://gitlab.com</serverUrl>
        <tag>v${project.version}</tag>
        <failOnExistingTagRelease>false</failOnExistingTagRelease>
        <overwriteTag>true</overwriteTag>
        <message>Release information for v${project.version}</message>
        <description>Description of your release</description>
        
        <!-- If your project has additional artifacts, such as ones produced by
             the maven-assembly-plugin, you can define the following -->
        <fileSets>
            <fileSet>
                <directory>${project.build.directory}</directory>
                <includes>
                    <include>${project.artifactId}*.tar.gz</include>
                    <include>${project.artifactId}*.zip</include>
                </includes>
            </fileSet>
        </fileSets>
    </configuration>
</plugin>
```

If unspecified explicitly, the plugin will upload the main artifact of your project and take the GitLab repo url from the `<scm>` section.

By default, the plugin will look for GitLab credentials in your maven `settings.xml`.
You can also inject through environmental variables as shown below.
```
<servers>
    <server>
        <id>gitlab</id>
        <username>${env.GITLAB_USERNAME}</username>
        <password>${env.GITLAB_PASSWORD}</password>
    </server>
</servers>
```
or can be
```
<servers>
    <server>
        <id>gitlab</id>
        <privateKey>${env.GITLAB_ACCESS_TOKEN}</privateKey>
    </server>
</servers>
```

These credentials can be overridden by setting `username` and `password` as system properties.

This work has been inspired from [jutzig/github-release-plugin](https://github.com/jutzig/github-release-plugin).
