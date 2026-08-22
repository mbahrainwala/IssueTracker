package behrainwala.issuetracker.web;

import behrainwala.issuetracker.dto.BrandingDtos.BrandingDto;
import behrainwala.issuetracker.dto.BrandingDtos.UpdateBrandingRequest;
import behrainwala.issuetracker.service.BrandingService;
import behrainwala.issuetracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/**
 * The two reads are open to anonymous callers - the login page is branded, so the name and
 * logo have to be fetchable before anyone has signed in. Every write is administrator-only.
 */
@RestController
@RequestMapping("/api/branding")
public class BrandingController {

    private final BrandingService brandingService;
    private final UserService userService;

    public BrandingController(BrandingService brandingService, UserService userService) {
        this.brandingService = brandingService;
        this.userService = userService;
    }

    @GetMapping
    public BrandingDto get() {
        return brandingService.get();
    }

    /**
     * The logo image, for an {@code <img>} tag.
     * <p>
     * Served with its real type - it has to render - but with sniffing off and a CSP that
     * denies everything and sandboxes the document. An SVG opened directly in a tab would
     * otherwise be a scripting context; sandboxed, it is only ever a picture.
     * <p>
     * Cached for an hour: the client appends the logo's version to the URL, so a replacement
     * arrives under a new address rather than waiting for this to expire.
     */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        BrandingService.Logo logo = brandingService.logo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(logo.bytes());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingDto update(@Valid @RequestBody UpdateBrandingRequest request, Authentication auth) {
        return brandingService.update(request, userService.currentUser(auth));
    }

    @PutMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingDto setLogo(@RequestParam("file") MultipartFile file, Authentication auth) {
        return brandingService.setLogo(file, userService.currentUser(auth));
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingDto clearLogo(Authentication auth) {
        return brandingService.clearLogo(userService.currentUser(auth));
    }
}
