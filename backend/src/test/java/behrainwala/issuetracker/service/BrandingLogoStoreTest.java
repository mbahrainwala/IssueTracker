package behrainwala.issuetracker.service;

import behrainwala.issuetracker.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard that keeps the logo out of the swept attachment directory. Getting this wrong
 * looks fine until the sweep runs hours later and the logo disappears, so it has to fail at
 * startup instead.
 */
class BrandingLogoStoreTest {

    private static BrandingLogoStore storeWith(String attachments, String branding) {
        AppProperties properties = new AppProperties();
        properties.getAttachments().setDirectory(attachments);
        properties.getBranding().setDirectory(branding);
        return new BrandingLogoStore(properties);
    }

    @Test
    void refusesTheSameDirectoryAsAttachments() {
        assertThatThrownBy(() -> storeWith("data/files", "data/files").rejectSharedDirectory())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nightly orphan sweep");
    }

    @Test
    void refusesADirectoryNestedInsideAttachments() {
        assertThatThrownBy(() -> storeWith("data/files", "data/files/branding").rejectSharedDirectory())
                .isInstanceOf(IllegalStateException.class);

        // Different spellings of the same place are still the same place.
        assertThatThrownBy(() -> storeWith("data/files", "data/files/../files/logo").rejectSharedDirectory())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsASiblingDirectory() {
        assertThatCode(() -> storeWith("data/attachments", "data/branding").rejectSharedDirectory())
                .doesNotThrowAnyException();
    }

    @Test
    void reportsNoLogoWhenTheDirectoryDoesNotExist() {
        BrandingLogoStore store = storeWith("data/attachments", "data/branding-does-not-exist");
        assertThat(store.exists()).isFalse();
        assertThat(store.read()).isEmpty();
    }
}
