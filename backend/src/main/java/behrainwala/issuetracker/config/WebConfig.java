package behrainwala.issuetracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Serialises pages through Spring Data's PagedModel DTO instead of PageImpl.
 *
 * Returning PageImpl directly serialises Spring's internal class, whose JSON shape carries no
 * stability guarantee and which logs a warning on every paged endpoint. VIA_DTO pins the
 * response to a documented structure: {"content":[...],"page":{size,number,totalElements,totalPages}}.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {
}
