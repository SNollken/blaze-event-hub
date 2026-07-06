package com.blaze.eventhub.event.entry;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
public class EntryController {

    private final EventEntryStore entryStore;
    private final EntryCalculator calculator;

    public EntryController(EventEntryStore entryStore, EntryCalculator calculator) {
        this.entryStore = entryStore;
        this.calculator = calculator;
    }

    @GetMapping("/entries")
    List<EventEntry> getEntries(@PathVariable String eventId) {
        return entryStore.findByEventId(eventId);
    }

    @PostMapping("/recalculate")
    int recalculate(@PathVariable String eventId) {
        return calculator.recalculateAll(eventId);
    }
}
