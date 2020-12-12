package tools.jvm.mvn;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.jcabi.xml.XML;
import org.xembly.Directive;
import org.xembly.Directives;

import java.util.Map;

public interface XemblyDirrective {

    Iterable<Directive> dirs(Project project, XML xml);

    ImmutableList<XemblyDirrective> PrepareSnapshot = ImmutableList.of(
            new PomDefaultStruc(),
            new PomDropDeps(),
            new PomParentRelPath()
    );

    ImmutableList<XemblyDirrective> ExecuteBuild = ImmutableList.of(
            new PomDefaultStruc(),
            new PomParentRelPath(),
            new PomDropDeps(),
            new AppendDeps()
    );

    class PomDefaultStruc implements XemblyDirrective {
        @Override
        public Iterable<Directive> dirs(Project project, XML xml) {
            return new Directives().xpath("/project").addIf("dependencies");
        }
    }

    class PomParentRelPath implements XemblyDirrective {
        @Override
        public Iterable<Directive> dirs(Project project, XML xml) {
            final Project.ProjectView view = project.toView();
            if (view.parent() == null) {
                return ImmutableList.of();
            }
            return new Directives()
                    .xpath("/project/parent")
                    .addIf("relativePath").set(view.parent());
        }
    }

    class DropPomParentRelPath implements XemblyDirrective {
        @Override
        public Iterable<Directive> dirs(Project project, XML xml) {
            return new Directives()
                    .xpath("/project/parent/relativePath")
                    .remove();
        }
    }

    class PomDropDeps implements XemblyDirrective {
        @Override
        public Iterable<Directive> dirs(Project project, XML xml) {
            return new Directives()
                    .xpath("/project/dependencies/dependency[not(@xe:remove=\"never\")]")
                    .remove();
        }
    }

    class AppendDeps implements XemblyDirrective {
        @Override
        public Iterable<Directive> dirs(Project project, XML xml) {
            final Iterable<Dep> deps = project.deps();
            if (Iterables.isEmpty(deps)) {
                return ImmutableList.of();
            }

            final Directives depsDir = new Directives()
                    .xpath("/project/dependencies");
            for (Dep dep : deps) {
                final Map<Object, Object> depProp = Maps.newLinkedHashMap();
                depProp.put("groupId", dep.groupId());
                depProp.put("artifactId", dep.artifactId());
                depProp.put("version", dep.version());
                depProp.put("scope", dep.scope());

                depsDir.add("dependency")
                        .comment(" by: " + dep.source() + " ")
                        .add(depProp)
                        .up();
            }
            return depsDir;
        }
    }
}
