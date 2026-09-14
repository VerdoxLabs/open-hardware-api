package de.verdox.hwapi.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Repairs legacy ordinal enum values created by older H2 dev schemas. */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAwinRegionRepair implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // H2 enum columns expose the stored enum value as a numeric value when
        // the enum was created by Hibernate. In the existing dev schema, 13 is DE.
        int repaired = jdbcTemplate.update(
                "UPDATE remote_active_listing SET primary_region = 'DE' "
                        + "WHERE CAST(primary_region AS VARCHAR) = '13'");
        if (repaired > 0) {
            System.out.println("Repaired " + repaired + " legacy Awin region values in H2");
        }
    }
}
