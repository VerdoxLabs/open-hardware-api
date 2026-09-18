package de.verdox.hwapi.admin;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Publishes committed catalog changes to connected admin clients. */
@Service
public class HardwareLiveUpdateService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, SseEmitter.event().name("connected").data("ready"));
        return emitter;
    }

    public void publishHardwareUpdated() {
        publish("hardware-updated");
    }

    public void publishScraperStatus() {
        publish("scraper-status");
    }

    private void publish(String eventName) {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name(eventName).data("updated"));
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            synchronized (emitter) {
                emitter.send(event);
            }
        } catch (IOException | RuntimeException error) {
            // The browser may close/reconnect an EventSource at any time. A
            // broken SSE connection must never affect the scraping worker.
            emitters.remove(emitter);
        }
    }
}
