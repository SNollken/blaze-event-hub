package com.blaze.eventhub.event.draw;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blaze.eventhub.common.NotFoundException;
import com.blaze.eventhub.member.Member;
import com.blaze.eventhub.member.MemberService;

@RestController
@RequestMapping("/api/events/{eventId}")
public class DrawController {

    private final DrawService drawService;
    private final MemberService memberService;

    public DrawController(DrawService drawService, MemberService memberService) {
        this.drawService = drawService;
        this.memberService = memberService;
    }

    @PostMapping("/draw")
    EventResultResponse executeDraw(@PathVariable String eventId) {
        Member current = memberService.getCurrentMember();
        return EventResultResponse.from(drawService.executeDraw(eventId, current.id()));
    }

    @GetMapping("/winner")
    EventResultResponse getWinner(@PathVariable String eventId) {
        return drawService.getWinner(eventId)
                .map(EventResultResponse::from)
                .orElseThrow(() -> new NotFoundException("Nenhum vencedor sorteado ainda para este evento."));
    }
}
