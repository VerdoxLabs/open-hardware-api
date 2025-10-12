package de.verdox.openhardwareapi.io.websites.xkom;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AbstractSingleXKomScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleXKomScraper(String id, String mindFactoryUrl, HardwareQuery<HARDWARE> query) {
        super(id, mindFactoryUrl, query);
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {

        var header = page.selectFirst("div.desc_section.head_desc");
        if (header != null) {
            specs.put("model", List.of(header.selectFirst("h2").text()));
        }

        var addAttr = page.select("div.additional-attributes");
        for (Element element : addAttr) {
            for (Element tr : element.select("tr")) {
                var label = tr.selectFirst("th.col.label").text();
                var data = tr.selectFirst("th.col.data").text();

                specs.put(label, Arrays.stream(data.split(",")).toList());
            }
        }
    }

    @Override
    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
        super.translateSpecsToTarget(specs, target);
        target.setModel(specs.get("model").getFirst());
        target.setManufacturer(specs.get("Hersteller").getFirst());
    }
}
