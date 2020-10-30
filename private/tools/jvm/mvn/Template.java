package tools.jvm.mvn;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.cactoos.Text;
import org.cactoos.io.InputOf;
import org.cactoos.text.TextOf;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.*;
import java.nio.charset.StandardCharsets;

public interface Template {

    /**
     * Evaluate template.
     * @return char source
     */
    Text eval();


    @Data
    static class PomPropsBean {
        final String groupId;
        final String artifactId;
        final String version;
    }

    /**
     * Template envelope via mustache.
     */
    @AllArgsConstructor
    class Mustache implements Template {

        private final ByteSource source;

        private final Object bean;

        @Override
        @SneakyThrows
        public Text eval() {
            final CharSource tplSource = source.asCharSource(StandardCharsets.UTF_8);
            MustacheFactory mf = new DefaultMustacheFactory();
            try (Reader tpl = tplSource.openStream()) {
                final StringWriter str = new StringWriter();
                try (Writer dest = new PrintWriter(str)) {
                    com.github.mustachejava.Mustache mustache = mf.compile(tpl, "template.mustache");
                    mustache.execute(dest, bean);
                }

                return new TextOf(new InputOf(str.toString()));
            }
        }
    }


    @AllArgsConstructor
    class Thymeleaf implements Template {

        /**
         * Builder factory.
         */
        private static final TemplateEngine ENGINE = new TemplateEngine();

        static {
            ENGINE.setTemplateResolver(new StringTemplateResolver());
        }

        private final CharSource source;


        @SneakyThrows
        @Override
        public Text eval() {
            return new TextOf(ENGINE.process(
                    source.read(),
                    new Context()
            ));
        }
    }

}
