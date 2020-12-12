package tools.jvm.mvn;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jcabi.xml.XML;
import lombok.AllArgsConstructor;
import org.w3c.dom.Node;
import org.xembly.Directive;
import org.xembly.Xembler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;
import static tools.jvm.mvn.Pom.SanitizedXML;
import static tools.jvm.mvn.Pom.XPATH_CONTEXT;

public interface PomTransformer {

    /**
     * Transform POM
     * @param pom a pom
     * @return new pom
     */
    public abstract Pom apply(Pom pom, Project project);


    /**
     * Transform by {@linkplain org.xembly.Xembler}
     */
    class Xembly implements PomTransformer {

        public Xembly() {
            this(Collections.emptySet());
        }

        public Xembly(Set<XemblyFeature> features) {
            this.features = features;
        }

        @AllArgsConstructor
        public enum XemblyFeature {
            ENABLED("xembly:on"),
            NO_PARENT_RELATIVE_PATH("xembly:no-parent-rel-path"),
            NO_DROP_DEPS("xembly:no-drop-deps");
            private final String qname;
        }

        /**
         * Xembly directives.
         */
        private final List<XemblyDirrective> actions = ImmutableList.of(
                new XemblyDirrective.PomDefaultStruc(),
                new XemblyDirrective.PomParentRelPath(),
                new XemblyDirrective.PomDropDeps(),
                new XemblyDirrective.AppendDeps()
        );

        private final Set<XemblyFeature> features;

        @Override
        public Pom apply(Pom pom, Project project) {
            return new Pom.Cached((Supplier<XML>) () -> getXML(pom, project));
        }

        private XML getXML(Pom pom, Project project) {
            final XML origXML = pom.xml();
            final SanitizedXML xml = new SanitizedXML(origXML);
            final Set<XemblyFeature> features = getFeatures(xml);
            features.addAll(this.features);
            if (!features.contains(XemblyFeature.ENABLED)) {
                return xml;
            }
            final List<XemblyDirrective> funcs = Lists.newArrayList(actions);
            if (features.contains(XemblyFeature.NO_DROP_DEPS)) {
                funcs.removeIf(func -> func instanceof XemblyDirrective.PomDropDeps);
            }
            if (features.contains(XemblyFeature.NO_PARENT_RELATIVE_PATH)) {
                funcs.removeIf(func -> func instanceof XemblyDirrective.PomParentRelPath);
                funcs.add(new XemblyDirrective.DropPomParentRelPath());
            }

            @SuppressWarnings("StaticPseudoFunctionalStyleMethod")
            final Iterable<Directive> dirs = concat(transform(funcs, d -> d.dirs(project, xml)));
            final XemblerAugment.XPathQuery query = xml.getXpathQuery();
            final Node node = XemblerAugment.applyQuietly(
                    XPATH_CONTEXT,
                    query,
                    dirs,
                    xml.node()
            );

            return new SanitizedXML(node);
        }


        /**
         * Xemlby features.
         * @return features
         */
        @SuppressWarnings("StaticPseudoFunctionalStyleMethod")
        private Set<XemblyFeature> getFeatures(XML xml) {
            Set<XemblyFeature> ll = Sets.newHashSet();
            final List<String> xpath1 = Lists.transform(xml.nodes("/project/comment()"), Objects::toString);
            final List<String> xpath2 = Lists.transform(xml.nodes("/comment()"), Objects::toString);
            concat(xpath1, xpath2).forEach(comment -> {
                for (XemblyFeature value : XemblyFeature.values()) {
                    if (comment.contains(value.qname)) {
                        ll.add(value);
                    }
                }
            });
            return ll;
        }
    }
}
