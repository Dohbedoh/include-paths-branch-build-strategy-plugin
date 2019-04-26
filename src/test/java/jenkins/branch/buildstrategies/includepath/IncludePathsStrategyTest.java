package jenkins.branch.buildstrategies.includepath;

import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.plugins.git.GitSCMFileSystem;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayOutputStream;

import static jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({IncludePathsStrategy.class, GitSCMSource.class, SCMFileSystem.class})
public class IncludePathsStrategyTest {

    private SCMHead head;
    private GitSCMSource source;
    private SCMRevisionImpl currRevision;
    private SCMRevisionImpl prevRevision;
    private SCM scm;

    @Before
    public void setUp() {
        GitSCMSource sourceMock = PowerMockito.mock(GitSCMSource.class);
        this.head = new SCMHead("test-branch");
        this.source = sourceMock;
        this.currRevision = new SCMRevisionImpl(head, "222");
        this.prevRevision = new SCMRevisionImpl(head, "111");
        this.scm = new GitSCM("http://acme.com");
    }

    @Test
    public void testPathsRegexps() throws Exception {
        assertTrue("Assert one path is included, single path provided",
                testStrategy(getCommit(), "src/dir3/.*\\.java"));
        assertTrue("Assert one path is included, single path provided",
                testStrategy(getCommit(), "src/.*\\.java"));
        assertTrue("Should pass if at least one path is included, multiple paths provided",
                testStrategy(getCommit(), "src/.*\\.java\nlib/.*\\.jar"));
        assertTrue("Should pass if at many path are included, multiple paths provided",
                testStrategy(getCommit(), "src/.*\\.txt\nsrc/.*\\.md"));
    }

    @Test
    public void shouldBuildIfAnyPathsIsIncluded() throws Exception {
        assertTrue(testStrategy(
                getCommit(),
                "src/.*\\.java\n" +
                        "lib/.*\\.jar"
        ));
    }

    @Test
    public void shouldlNotBuildIfNoPathsIsIncluded() throws Exception {
        assertFalse(testStrategy(getCommit(), "src/.*\\.rb\nlib/.*\\.jar"));
    }

    @Test
    public void shouldBuildIfChangelogEmpty() throws Exception {
        assertTrue(testStrategy("", "src/.*\\.java\nlib/.*\\.jar"));
    }

    @Test
    public void shouldBuildIfChangelogBroken() throws Exception {
        assertTrue(testStrategy("this is not a changelog", "src/.*\\.java\nlib/.*\\.jar"));
    }

    private boolean testStrategy(String changelog, String includedPaths) throws Exception {
        PowerMockito.mockStatic(SCMFileSystem.class);
        GitSCMFileSystem gitScmFileSystemMock = Mockito.mock(GitSCMFileSystem.class);
        ByteArrayOutputStream ByteArrayOutputStreamMock = Mockito.mock(ByteArrayOutputStream.class);
        Mockito.when(ByteArrayOutputStreamMock.toByteArray()).thenReturn(changelog.getBytes());
        TaskListener taskListenerMock = Mockito.mock(TaskListener.class);
        try {
            Mockito.when(source.build(head, currRevision)).thenReturn(scm);
            Mockito.when(SCMFileSystem.of(source, head, currRevision)).thenReturn(gitScmFileSystemMock);
            Mockito.when(ByteArrayOutputStreamMock.toByteArray()).thenReturn(changelog.getBytes());
            Mockito.when(gitScmFileSystemMock.changesSince(prevRevision, ByteArrayOutputStreamMock)).thenReturn(true);
            Mockito.when(taskListenerMock.getLogger()).thenReturn(System.out);
            PowerMockito.whenNew(ByteArrayOutputStream.class).withNoArguments().thenReturn(ByteArrayOutputStreamMock);
            return new IncludePathsStrategy(includedPaths).isAutomaticBuild(source, head, currRevision, prevRevision, taskListenerMock);
        } catch (Exception e) {
            throw e;
        }
    }

    private String getCommit() {
        return "commit 59b1b3622654d3ecc9f8c5f9269ea2757a0e9112\n" +
                "tree a8844ebc5610af5dd9cc76675e6d5235249b7340\n" +
                "parent ec882255b45869a2cbc88b1e1e41ae800b843eea\n" +
                "authorzz alice <alice@acme.com> 2019-04-24 12:45:11 +1000\n" +
                "committer GitHub Enterprise <alice@acme.com> 2019-04-24 12:45:11 +1000\n" +
                "\n" +
                "    Create test.md\n" +
                "\n" +
                "\n" +
                "\n" +
                ":000000 100644 0000000000000000000000000000000000000000 cdc5388a9b1f17445a9900f4cc6d5d6218c5aff6 A\tsrc/dir1/test.md\n" +
                ":000000 100644 0000000000000000000000000000000000000000 cdc5388a9b1f17445a9900f4cc6d5d6218c5aff6 A\tsrc/dir2/test.txt\n" +
                ":000000 100644 0000000000000000000000000000000000000000 cdc5388a9b1f17445a9900f4cc6d5d6218c5aff6 A\tsrc/dir3/test.java";
    }
}
