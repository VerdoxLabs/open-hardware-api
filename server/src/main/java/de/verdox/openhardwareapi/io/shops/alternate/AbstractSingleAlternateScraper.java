package de.verdox.openhardwareapi.io.shops.alternate;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbstractSingleAlternateScraper <HARDWARE extends HardwareSpec> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleAlternateScraper(String mindFactoryUrl, HardwareQuery<HARDWARE> query) {
        super(mindFactoryUrl, query);
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {
        var productNameDiv = page.selectFirst("div.product-name");
        if(productNameDiv == null){
            return;
        }
        StringBuilder title = new StringBuilder();
        for (Element span : productNameDiv.select("span")) {
            title.append(" ").append(span.text());
        }
        specs.put("model", List.of(title.toString()));

        var productDetails = page.selectFirst("div.card nav-product-details");
        for (Element tr : productDetails.select("tr")) {
            var columnTitleTr = tr.selectFirst("td.c1");
            var columnSubTitleTr = tr.selectFirst("td.c2");
            var columnValueTr = tr.selectFirst("td.c4");

            String columnTitle = columnSubTitleTr != null ? columnSubTitleTr.text() : columnTitleTr != null ? columnTitleTr.text() : "";
            if(columnTitle.isBlank() || columnValueTr == null) {
                continue;
            }

            specs.put(columnTitle, List.of(columnValueTr.text()));

            if(columnTitle.equals("EAN")) {
                specs.put("ean", List.of(columnValueTr.text()));
            }

            if(columnTitle.equals("Hersteller-Nr.")) {
                specs.put("mpn", List.of(columnValueTr.text()));
            }


        }
    }

    @Override
    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
        super.translateSpecsToTarget(specs, target);
        target.setModel("model");
    }

    @Override
    protected void extractNumbers(Document page, String[] numbers, Map<String, List<String>> specs) {
        setEAN(specs.getOrDefault("ean", new ArrayList<>()).getFirst(), numbers);
        setMPN(specs.getOrDefault("mpn", new ArrayList<>()).getFirst(), numbers);
    }
}
