package de.verdox.openhardwareapi.io.websites.mindfactory;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AbstractSingleMindfactoryScraper<HARDWARE extends HardwareSpec> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleMindfactoryScraper(String mindFactoryUrl, HardwareQuery<HARDWARE> constructor) {
        super(mindFactoryUrl, constructor);
    }

    @Override
    protected void extractNumbers(Document page, String[] numbers, Map<String, List<String>> specs) {
        setEAN(page.select("span.product-ean").text(), numbers);
        for (Element visibleXs : page.select("div.visible-xs")) {
            var h1 = visibleXs.selectFirst("h1");
            if(h1 != null && h1.attr("data-original-font-size").equals("18.2")) {
                specs.put("model", List.of(visibleXs.text()));
                break;
            }
        }
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {
        int counter = 0;
        String nextKey = null;
        List<String> nextValue = null;
        for (Element td : page.select("td")) {
            if (counter % 2 == 0) {
                nextKey = td.text();
            } else {
                nextValue = Arrays.stream(td.text().replace("nicht vorhanden", "false").split(", ")).toList();

            }

            if (nextKey != null) {
                specs.put(nextKey, nextValue);
                nextKey = null;
                nextValue = null;
            }
            counter++;
        }
    }

    @Override
    public int getAmountTasks() {
        return 0;
    }
}
