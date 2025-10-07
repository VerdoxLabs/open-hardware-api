package de.verdox.openhardwareapi.io.shops.caseking;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Map;

public class AbstractSingleCasekingScraper<HARDWARE extends HardwareSpec> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleCasekingScraper(String mindFactoryUrl, HardwareQuery<HARDWARE> query) {
        super(mindFactoryUrl, query);
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {
        String model = page.selectFirst("h1.product-name").text().split(",")[0];
        specs.put("model", List.of(model));

        for (Element specLv1 : page.selectFirst("#product-detail-content-accordion-body-id-specification").selectFirst("div.table-responsive").select("tr.spec-lvl-1")) {
            var elements = specLv1.select("td");
            specs.put(elements.get(0).text(), List.of(elements.get(1).text().replace("Ja", "true").replace("Nein", "false")));
        }

        for (Element specLv2 : page.selectFirst("#product-detail-content-accordion-body-id-specification").selectFirst("div.table-responsive").select("tr.spec-lvl-2")) {
            var elements = specLv2.select("td");
            specs.put(elements.get(0).text(), List.of(elements.get(1).text().replace("Ja", "true").replace("Nein", "false")));
        }

        for (Element specLv3 : page.selectFirst("#product-detail-content-accordion-body-id-specification").selectFirst("div.table-responsive").select("tr.spec-lvl-3")) {
            var elements = specLv3.select("td");
            specs.put(elements.get(0).text(), List.of(elements.get(1).text().replace("Ja", "true").replace("Nein", "false")));
        }

        Elements productAttributes = page.select("div.product-attributes");
        for (Element productAttribute : productAttributes) {
            for (Element element : productAttribute.select("div.d-inline-block")) {
                String label = element.select("span.product-attributes-label").text();
                String value = element.select("span.product-attributes-value").text();

                if(label.equalsIgnoreCase("hersteller")) {
                    specs.put("Hersteller", List.of(value));
                }
            }
        }
    }

    @Override
    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
        target.setManufacturer(specs.get("Hersteller").getFirst());
        target.setModel(specs.get("model").getFirst());
    }

    @Override
    protected void extractNumbers(Document page, String[] numbers, Map<String, List<String>> specs) {
        String EAN = "";
        String MPN = "";
        Elements productAttributes = page.select("div.product-attributes");
        for (Element productAttribute : productAttributes) {
            for (Element element : productAttribute.select("div.d-inline-block")) {
                String label = element.select("span.product-attributes-label").text();
                String value = element.select("span.product-attributes-value").text();

                if (label.equals("EAN")) {
                    EAN = value;
                }

                if (label.equals("MPN")) {
                    MPN = value;
                }
            }
        }
        setEAN(EAN, numbers);
        setMPN(MPN, numbers);
    }
}
