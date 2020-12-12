package tools.jvm.mvn;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import picocli.CommandLine;

import java.io.File;
import java.util.*;

/**
 * Args.
 */
@Accessors(fluent = true)
public class Args  {
    private final List<String> goals = Lists.newArrayList();
    private final List<String> profiles = Lists.newArrayList();
    private final Map<FlagsKey, Object> ctx = Maps.newHashMap();
    
    enum FlagsKey {
        SETTINGS_XML,
        LOCAL_REPOSITORY;
    }

    public Args() {
    }

    public Args(Args args) {
        this.goals.addAll(args.goals);
        this.profiles.addAll(args.profiles);
        this.offline = args.offline;
        this.ctx.putAll(args.ctx);
    }

    @CommandLine.Command(name = "")
    private static class CmdFlags {

        @CommandLine.Option(names = {"-P", "--active-profiles"}, split = ",")
        public List<String> profiles = Lists.newArrayList();
        @CommandLine.Option(names = {"--goals"}, split = ",")
        public List<String> goals = Lists.newArrayList();

    }
    @Setter
    @Getter
    private boolean offline;

    /**
     * Add args to set.
     * @param s args
     * @return this
     */
    @SuppressWarnings("UnusedReturnValue")
    public Args append(String...s) {
        goals.addAll(Arrays.asList(s));
        return this;
    }

    /**
     * Union arguments.
     * @param other other args
     * @return combined.
     */
    public Args merge(Args other) {
        final Args argsNew = new Args();
        argsNew.goals.addAll(Sets.newHashSet(
                Iterables.concat(this.goals, other.goals)
        ));
        argsNew.profiles.addAll(Sets.newHashSet(
                Iterables.concat(this.profiles, other.profiles)
        ));
        argsNew.ctx.putAll(this.ctx);
        argsNew.ctx.putAll(other.ctx);
        return argsNew;
    }

    /**
     * Add args to set.
     * @param s args
     * @return this
     */
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public Args profile(String...s) {
        profiles.addAll(Arrays.asList(s));
        return this;
    }

    /**
     * Add args to set.
     * @param key args
     * @param o value
     * @return this
     */
    @SuppressWarnings("UnusedReturnValue")
    public Args tag(FlagsKey key, Object o) {
        ctx.put(key, o);
        return this;
    }

    /**
     * Get settings value.
     * @param key key
     * @return value of setting
     */
    @SuppressWarnings("UnusedReturnValue")
    public Object tag(FlagsKey key) {
        return ctx.get(key);
    }

    /**
     * Append from command line string.
     * @param cmdLine line
     * @return args
     */
    @SuppressWarnings("UnusedReturnValue")
    public Args parseCommandLine(String cmdLine) {
        final String[] flags = cmdLine.split(" ");
        final CmdFlags params = resolve(flags);

        this.goals.addAll(params.goals);
        this.profiles.addAll(params.profiles);
        return this;
    }

    private CmdFlags resolve(String[] flags) {
        final CmdFlags params;
        try {
            params = new CmdFlags();
            final CommandLine.ParseResult result = new CommandLine(params).parseArgs(flags);
            Preconditions.checkState(!result.isUsageHelpRequested());
            Preconditions.checkState(!result.isVersionHelpRequested());
        } catch (CommandLine.ParameterException e) {
            throw new ToolException("[Args] flags " +Arrays.toString(flags)   + " not valid: "
                    + e.getMessage() + "\n correct flags: "
                    + e.getCommandLine().getHelp().synopsis(0)
            );
        }
        return params;
    }

    public InvocationRequest toInvocationRequest() {
        final DefaultInvocationRequest request = new DefaultInvocationRequest();
        request.setGoals(Lists.newArrayList(Sets.newLinkedHashSet(goals)));
        request.setProfiles(Lists.newArrayList(Sets.newLinkedHashSet(profiles)));
        if (offline) {
            request.setOffline(true);
        }
        if (ctx.containsKey(FlagsKey.SETTINGS_XML)) {
            request.setUserSettingsFile((File) ctx.get(FlagsKey.SETTINGS_XML));
        }
        if (ctx.containsKey(FlagsKey.LOCAL_REPOSITORY)) {
            request.setLocalRepositoryDirectory((File) ctx.get(FlagsKey.LOCAL_REPOSITORY));
        }
        return request;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("goals", goals)
                .add("profiles", profiles)
                .add("ctx", ctx)
                .toString();
    }
}
