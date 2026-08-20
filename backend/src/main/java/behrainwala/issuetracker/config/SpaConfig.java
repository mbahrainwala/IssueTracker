package behrainwala.issuetracker.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the bundled React app out of the executable jar and sends unknown, non-API
 * paths to index.html so client-side routes such as /projects/PROJ1 survive a page
 * refresh or a pasted link.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SpaConfig.class);
    private static final String UI_ROOT = "classpath:/META-INF/resources/";

    /**
     * Without this, a missing bundle shows up only as a Whitelabel Error Page on "/",
     * which says nothing about the actual cause.
     */
    @PostConstruct
    void warnIfUiMissing() {
        if (!new ClassPathResource("META-INF/resources/index.html").exists()) {
            log.warn("""
                    No React bundle on the classpath - "/" will return an error page.
                    The API itself is fine. Either build the UI into the jar:
                        mvn clean package        (from the project root, not backend/)
                    or run the UI separately in dev:
                        cd frontend && npm run dev     -> http://localhost:5173""");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(UI_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Never mask a missing API route with the SPA shell - those must still 404.
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
