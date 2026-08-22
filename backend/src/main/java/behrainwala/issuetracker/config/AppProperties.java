package behrainwala.issuetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Attachments attachments = new Attachments();
    private final Branding branding = new Branding();

    public Branding getBranding() {
        return branding;
    }

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
        private String sweepCron = "0 30 3 * * *";
        private String sweepZone = "UTC";
        /**
         * How old a file with no row must be before the sweep will remove it. Covers the gap
         * between an upload writing its bytes and committing its row.
         */
        private Duration orphanGrace = Duration.ofHours(6);

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

        public String getSweepCron() {
            return sweepCron;
        }

        public void setSweepCron(String sweepCron) {
            this.sweepCron = sweepCron;
        }

        public String getSweepZone() {
            return sweepZone;
        }

        public void setSweepZone(String sweepZone) {
            this.sweepZone = sweepZone;
        }

        public Duration getOrphanGrace() {
            return orphanGrace;
        }

        public void setOrphanGrace(Duration orphanGrace) {
            this.orphanGrace = orphanGrace;
        }
    }

    /**
     * Company name and logo in the title bar. The logo is a file on disk, and its directory
     * must sit outside {@link Attachments#getDirectory()} - the nightly orphan sweep deletes
     * anything in there that no attachment row claims, and the logo never will.
     */
    public static class Branding {
        private String directory = "data/branding";
        private long maxLogoBytes = 512L * 1024;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public long getMaxLogoBytes() {
            return maxLogoBytes;
        }

        public void setMaxLogoBytes(long maxLogoBytes) {
            this.maxLogoBytes = maxLogoBytes;
        }
    }
}
