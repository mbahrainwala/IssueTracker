package behrainwala.issuetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Attachments attachments = new Attachments();

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public static class Jwt {
        private String secret;
        private long expirationMinutes = 720;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /**
     * Where ticket documents are kept and how large they may be. The directory holds only
     * opaque UUID files and must sit outside anything the server publishes statically.
     */
    public static class Attachments {
        private String directory = "data/attachments";
        private long maxSizeBytes = 10L * 1024 * 1024;
        private int maxPerTicket = 20;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public long getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(long maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
        }

        public int getMaxPerTicket() {
            return maxPerTicket;
        }

        public void setMaxPerTicket(int maxPerTicket) {
            this.maxPerTicket = maxPerTicket;
        }
    }
}
