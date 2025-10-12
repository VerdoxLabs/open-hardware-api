package de.verdox.openhardwareapi.io.websites.alternate;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Map;

public class AbstractSingleAlternateScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleAlternateScraper(String id, String mindFactoryUrl, HardwareQuery<HARDWARE> query) {
        super(id, mindFactoryUrl, query);
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {

    }

    @Override
    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
        super.translateSpecsToTarget(specs, target);
        target.setModel(specs.get("model").getFirst());
    }
}
