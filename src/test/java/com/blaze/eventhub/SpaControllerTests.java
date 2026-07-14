package com.blaze.eventhub;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SpaControllerTests {

    private final MockMvc mockMvc = standaloneSetup(new SpaController()).build();

    @ParameterizedTest
    @ValueSource(strings = {"/events/event-1/manage", "/settings/blaze", "/studio/channel", "/login"})
    void forwardsApplicationDeepLinksToReact(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
