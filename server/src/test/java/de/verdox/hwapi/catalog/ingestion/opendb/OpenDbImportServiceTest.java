package de.verdox.hwapi.catalog.ingestion.opendb;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.catalog.domain.CPU;
import de.verdox.hwapi.catalog.domain.GPU;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenDbImportServiceTest {
    private final OpenDbImportService service = new OpenDbImportService(
            new OpenDbProperties(false, "", "main", Path.of("target/opendb-test"), null),
            mock(OpenDbRepository.class), mock(de.verdox.hwapi.catalog.application.HardwareSpecService.class),
            mock(de.verdox.hwapi.pricing.application.RemoteActiveListingWriterService.class),
            new ObjectMapper());

    @Test
    void mapsCpuIdentityAndNestedSpecifications() throws Exception {
        var json = new ObjectMapper().readTree("""
                {
                  "opendb_id":"11111111-1111-1111-1111-111111111111",
                  "socket":"AM5",
                  "microarchitecture":"Zen 4",
                  "cores":{"total":8,"performance":8,"efficiency":0,"threads":16},
                  "clocks":{"performance":{"base":4.2,"boost":5.0}},
                  "cache":{"l3":96},
                  "specifications":{"tdp":120,"memory":{"channels":2,"types":["DDR5"]}},
                  "metadata":{"name":"Ryzen 7 7800X3D","manufacturer":"AMD","part_numbers":["100-000000910"]},
                  "identifiers":{"identifiers":[{"type":"ean","value":"0730143315609"},{"type":"mpn","value":"100-000000910"}]}
                }
                """);

        CPU cpu = (CPU) service.parse("CPU", json);

        assertThat(cpu.getManufacturer()).isEqualTo("AMD");
        assertThat(cpu.getModel()).isEqualTo("Ryzen 7 7800X3D");
        assertThat(cpu.getSocket().name()).isEqualTo("AM5");
        assertThat(cpu.getCores()).isEqualTo(8);
        assertThat(cpu.getThreads()).isEqualTo(16);
        assertThat(cpu.getBoostClockMhz()).isEqualTo(5.0);
        assertThat(cpu.getEANs()).contains("0730143315609");
        assertThat(cpu.getMPNs()).contains("100-000000910");
    }

    @Test
    void mapsGpuFieldsWithoutRequiringRetailerData() throws Exception {
        var json = new ObjectMapper().readTree("""
                {
                  "chipset":"GeForce RTX 4070",
                  "memory":12,
                  "memory_type":"GDDR6X",
                  "core_base_clock":1920,
                  "core_boost_clock":2475,
                  "tdp":200,
                  "length":310,
                  "metadata":{"name":"Example RTX 4070","manufacturer":"ASUS","part_numbers":["X-4070"]},
                  "identifiers":{"identifiers":[{"type":"mpn","value":"X-4070"}]}
                }
                """);

        GPU gpu = (GPU) service.parse("GPU", json);

        assertThat(gpu.getGpuCanonicalName()).isEqualTo("GeForce RTX 4070");
        assertThat(gpu.getVramGb()).isEqualTo(12);
        assertThat(gpu.getVramType().name()).isEqualTo("GDDR6X");
        assertThat(gpu.getLengthMm()).isEqualTo(310);
    }

    @Test
    void rejectsRecordsWithoutStableProductIdentity() throws Exception {
        var json = new ObjectMapper().readTree("{\"metadata\":{\"name\":\"Unknown\"}}");

        assertThat(service.parse("CPU", json)).isNull();
    }
}
