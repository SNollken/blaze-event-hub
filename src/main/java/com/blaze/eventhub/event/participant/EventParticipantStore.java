package com.blaze.eventhub.event.participant;

import java.util.List;

public interface EventParticipantStore {

    boolean saveIfAbsent(EventParticipant participant);

    List<EventParticipant> findByEventId(String eventId);

    int countByEventId(String eventId);

    /**
     * Retorna a contagem bruta de ações de um usuário para um evento e tipo de ação.
     */
    int getRawActionCount(String eventId, String blazeUserId, String actionType);

    /**
     * Incrementa a contagem bruta de ações (usado quando usuário já existe como participante).
     */
    void incrementRawActionCount(String eventId, String blazeUserId, String actionType);
}
