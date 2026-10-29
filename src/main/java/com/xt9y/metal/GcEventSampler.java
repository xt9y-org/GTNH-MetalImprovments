package com.xt9y.metal;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

/**
 * Reports actual HotSpot GC notifications during a benchmark, rather than
 * inferring GC timing from once-per-second MXBean counters.
 *
 * Notification callback times are approximate: the callback runs after GC.
 * Never installs a listener when a benchmark is inactive.
 */
public final class GcEventSampler {

    public static final class Event {

        public final long receivedNanos;
        public final long durationMillis;
        public final String name;
        public final String cause;

        Event(long receivedNanos, long durationMillis, String name, String cause) {
            this.receivedNanos = receivedNanos;
            this.durationMillis = durationMillis;
            this.name = name;
            this.cause = cause;
        }
    }

    private static final int MAX_EVENTS = 256;
    private final List<Event> events = new ArrayList<>();
    private final List<NotificationEmitter> emitters = new ArrayList<>();
    private final NotificationListener listener = this::onNotification;
    private volatile boolean collecting;

    public void begin() {
        end();
        synchronized (events) {
            events.clear();
        }
        collecting = true;
        try {
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean instanceof NotificationEmitter) {
                    NotificationEmitter emitter = (NotificationEmitter) bean;
                    emitter.addNotificationListener(listener, null, null);
                    emitters.add(emitter);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            MetalImprovements.LOG.warn("GC notifications unavailable; MXBean totals remain available", e);
            end();
        }
    }

    private void onNotification(Notification notification, Object ignored) {
        if (!collecting
            || !GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType()))
            return;
        if (!(notification.getUserData() instanceof CompositeData)) return;
        try {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo
                .from((CompositeData) notification.getUserData());
            if (info == null || info.getGcInfo() == null) return;
            Event event = new Event(
                System.nanoTime(),
                Math.max(
                    0L,
                    info.getGcInfo()
                        .getDuration()),
                info.getGcName(),
                info.getGcCause());
            synchronized (events) {
                if (collecting && events.size() < MAX_EVENTS) events.add(event);
            }
        } catch (RuntimeException e) {
            MetalImprovements.LOG.debug("Unable to decode a GC notification", e);
        }
    }

    public Event[] end() {
        collecting = false;
        for (NotificationEmitter emitter : emitters) {
            try {
                emitter.removeNotificationListener(listener);
            } catch (Exception ignored) {
                // A collector may have disappeared while the benchmark ran.
            }
        }
        emitters.clear();
        synchronized (events) {
            return events.toArray(new Event[0]);
        }
    }
}
