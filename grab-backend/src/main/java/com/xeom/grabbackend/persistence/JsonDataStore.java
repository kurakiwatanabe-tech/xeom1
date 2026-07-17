package com.xeom.grabbackend.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

@Component
public class JsonDataStore {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path dbPath = Paths.get("data", "db.json");
    private AppData appData;

    @PostConstruct
    public void init() throws IOException {
        if (!Files.exists(dbPath)) {
            Files.createDirectories(dbPath.getParent());
            AppData initial = new AppData();
            initial.setCustomers(new ArrayList<>());
            initial.setDrivers(new ArrayList<>());
            initial.setTrips(new ArrayList<>());
            save(initial);
        }
        appData = load();
    }

    public synchronized AppData load() throws IOException {
        if (appData != null) {
            return appData;
        }
        if (!Files.exists(dbPath)) {
            AppData initial = new AppData();
            initial.setCustomers(new ArrayList<>());
            initial.setDrivers(new ArrayList<>());
            initial.setTrips(new ArrayList<>());
            save(initial);
            return initial;
        }
        return objectMapper.readValue(Files.readString(dbPath), AppData.class);
    }

    public synchronized void save(AppData data) throws IOException {
        Files.writeString(dbPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        this.appData = data;
    }
}
