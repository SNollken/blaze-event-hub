package com.nollen.blaze.alert;

import java.util.List;

public record AlertPageResponse(
		List<Alert> alerts,
		long total) {
}
