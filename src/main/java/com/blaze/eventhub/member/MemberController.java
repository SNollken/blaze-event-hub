package com.blaze.eventhub.member;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping("/me")
	MemberProfileResponse me() {
		return MemberProfileResponse.from(memberService.getCurrentMember());
	}

	@GetMapping("/{id}")
	MemberPublicResponse byId(@PathVariable String id) {
		return MemberPublicResponse.from(memberService.findById(id));
	}
}
