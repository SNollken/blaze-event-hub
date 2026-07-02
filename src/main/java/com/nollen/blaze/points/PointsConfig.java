package com.nollen.blaze.points;

import com.nollen.blaze.common.IdGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PointsConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PointsConfig.class);

    private final PointsStore store;
    private final IdGenerator idGenerator;

    public PointsConfig(PointsStore store, IdGenerator idGenerator) {
        this.store = store;
        this.idGenerator = idGenerator;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!store.findAllRules().isEmpty()) {
            return;
        }

        log.info("Seeding default points rules...");

        store.saveRule(new PointsRule(idGenerator.newId(), "FOLLOW", 10, "Follow na live", true));
        store.saveRule(new PointsRule(idGenerator.newId(), "SUBSCRIPTION", 100, "Inscricao", true));
        store.saveRule(new PointsRule(idGenerator.newId(), "DONATION", 50, "Doacao", true));
        store.saveRule(new PointsRule(idGenerator.newId(), "RAID", 25, "Raid", true));
        store.saveRule(new PointsRule(idGenerator.newId(), "CHAT_MESSAGE", 1, "Mensagem no chat", true));

        log.info("Default points rules seeded: {}", store.findAllRules().size());
    }
}
