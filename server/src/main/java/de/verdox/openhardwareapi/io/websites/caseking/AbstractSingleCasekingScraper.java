package de.verdox.openhardwareapi.io.websites.caseking;

import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbstractSingleCasekingScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends SinglePageHardwareScraper<HARDWARE> {
    public AbstractSingleCasekingScraper(String id, String mindFactoryUrl, HardwareQuery<HARDWARE> query) {
        super(id, mindFactoryUrl, query);
    }

    @Override
    protected void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable {

    }


    @Override
    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
        target.setManufacturer(specs.get("manufacturer").getFirst());
        target.setModel(specs.get("model").getFirst());
    }
}
