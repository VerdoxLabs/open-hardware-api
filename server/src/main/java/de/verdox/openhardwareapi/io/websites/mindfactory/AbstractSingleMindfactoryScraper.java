package de.verdox.openhardwareapi.io.websites.mindfactory;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AbstractSingleMindfactoryScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleMindfactoryScraper(String id, String mindFactoryUrl, HardwareQuery<HARDWARE> constructor) {
        super(id, mindFactoryUrl, constructor);
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {
        specs.put("EAN", List.of(page.select("span.product-ean").text()));

        for (Element visibleXs : page.select("div.visible-xs")) {
            var h1 = visibleXs.selectFirst("h1");
            if (h1 != null && h1.attr("data-original-font-size").equals("18.2")) {
                specs.put("model", List.of(visibleXs.text()));
                break;
            }
        }

        for (Element mat10 : page.select("div.mat10")) {
            for (Element img : mat10.select("img")) {
                String alt = img.attr("alt");
                String title = img.attr("title");
                if(alt.equalsIgnoreCase(title)) {
                    specs.put("manufacturer", List.of(title));
                }
            }
        }

        int counter = 0;
        String nextKey = null;
        List<String> nextValue = null;
        for (Element td : page.select("td")) {
            if (counter % 2 == 0) {
                nextKey = td.text();
            } else {
                nextValue = Arrays.stream(td.text().replace("nicht vorhanden", "false").split(", ")).toList();
            }

            if (nextKey != null && nextValue != null) {
                specs.put(nextKey, nextValue);
                nextKey = null;
                nextValue = null;
            }
            counter++;
        }
    }

    @Override
    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
        super.translateSpecsToTarget(specs, target);
        if (specs.containsKey("model") && !specs.get("model").isEmpty()) {
            target.setModel(specs.get("model").getFirst());
        }
        if(specs.containsKey("manufacturer") && !specs.get("manufacturer").isEmpty()) {
            target.setManufacturer(specs.get("manufacturer").getFirst());
        }
    }

    @Override
    public int getAmountTasks() {
        return 0;
    }
}
