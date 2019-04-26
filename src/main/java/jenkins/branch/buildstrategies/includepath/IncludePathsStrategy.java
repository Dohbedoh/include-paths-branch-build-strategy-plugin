package jenkins.branch.buildstrategies.includepath;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.*;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Strategy to decide whether or not to build based on affected files in SCM changes.
 * 
 * TODO: Make it abstract with method to either getFiles from the output stream that the changelog is written to. Each 
 * SCM may implement its own parser.
 */
public class IncludePathsStrategy extends BranchBuildStrategy {

    private static final Logger LOGGER = Logger.getLogger(IncludePathsStrategy.class.getName());
    private final String includedPaths;

    @DataBoundConstructor
    public IncludePathsStrategy(String includedPaths) {
        this.includedPaths = includedPaths;
    }

    /**
     * Determine if build is required by checking if any of the affected paths are in the included paths lists.
     * <p>
     * {@inheritDoc}
     *
     * @return true if changeset have commits that includes provided paths
     */
    @Override
    public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head, @NonNull SCMRevision currRevision, @NonNull SCMRevision prevRevision, @NonNull TaskListener taskListener) {

        try {

            // Returns early if no path is provided
            if (includedPaths.trim().isEmpty()) {
                return true;
            }

            SCMFileSystem fileSystem = SCMFileSystem.of(source, head, currRevision);

            if (fileSystem == null) {
                taskListener.error("Error retrieving SCMFileSystem");
                return true;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            if (prevRevision != null && !(prevRevision instanceof AbstractGitSCMSource.SCMRevisionImpl)) {
                fileSystem.changesSince(new AbstractGitSCMSource.SCMRevisionImpl(head, prevRevision.toString().substring(0, 40)), out);
            } else {
                fileSystem.changesSince(prevRevision, out);
            }

            GitChangeLogParser parser = new GitChangeLogParser(true);
            List<GitChangeSet> logs = parser.parse(new ByteArrayInputStream(out.toByteArray()));
            List<String> includedPathsList = Arrays.stream(includedPaths.split("\n"))
                    .map(e -> e.trim().toLowerCase())
                    .collect(Collectors.toList());
            LOGGER.fine(String.format("Included paths: %s", includedPathsList.toString()));

            return logs.isEmpty() || logs.parallelStream()
                    .map(GitChangeSet::getAffectedPaths)
                    .flatMap(Collection::stream)
                    .anyMatch(m -> isAffectedPathIncluded(m, includedPathsList));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception", e);
            return true;
        }

    }

    private boolean isAffectedPathIncluded(String path, Collection<String> includedPathsList) {
        return includedPathsList.stream()
                .map(s -> FileSystems.getDefault().getPathMatcher("regex:" + s))
                .parallel()
                .anyMatch(matcher -> matcher.matches(Paths.get(path.replace('\\', '/'))));
    }

    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {
        
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Include Paths Strategy";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(@Nonnull SCMSourceDescriptor sourceDescriptor) {
            return GitSCMSource.DescriptorImpl.class.isAssignableFrom(sourceDescriptor.getClass());
        }
    }

}
