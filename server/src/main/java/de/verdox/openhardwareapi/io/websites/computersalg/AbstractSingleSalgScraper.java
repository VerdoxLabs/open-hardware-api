package de.verdox.openhardwareapi.io.websites.computersalg;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AbstractSingleSalgScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleSalgScraper(String id, String url, HardwareQuery<HARDWARE> query) {
        super(id, url, query);
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {

    }

    @Override
    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
        super.translateSpecsToTarget(specs, target);
        if(specs.containsKey("manufacturer")) {
            target.setManufacturer(specs.get("manufacturer").getFirst());
        }
        if(specs.containsKey("model")) {
            target.setManufacturer(specs.get("model").getFirst());
        }
    }
}
