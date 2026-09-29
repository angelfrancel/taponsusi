package com.taponsusi.platform.simulation;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.security.JwtService;
import com.taponsusi.waitlist.WaitlistOfferActionHandler;
import com.taponsusi.waitlist.WaitlistOfferActionResult;
import com.taponsusi.waitlist.WaitlistOfferNotAcceptableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulationWaitlistActionControllerTest {

    private static final String PATH = "/api/v1/simulation/waitlist-actions";
    private static final Long SELLER_ID = 101L;
    private static final String BUYER_ID = "SIM_BUYER_0";
    private static final String ACCEPT_PAYLOAD = "WAITLIST_ACCEPT:401";
    private static final String DECLINE_PAYLOAD = "WAITLIST_DECLINE:401";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private WaitlistOfferActionHandler actionHandler;

    @Test
    void acceptReturnsOrderAndUsesAuthenticatedSeller() throws Exception {
        when(actionHandler.handle(SELLER_ID, BUYER_ID, ACCEPT_PAYLOAD))
                .thenReturn(Optional.of(WaitlistOfferActionResult.accepted(401L, 501L)));

        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sellerId":999,"buyerPlatformId":"SIM_BUYER_0",
                                 "payload":"WAITLIST_ACCEPT:401"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("ACCEPT"))
                .andExpect(jsonPath("$.data.waitlistEntryId").value(401))
                .andExpect(jsonPath("$.data.createdOrderId").value(501))
                .andExpect(jsonPath("$.data.nextOfferedEntryId").isEmpty());

        verify(actionHandler).handle(SELLER_ID, BUYER_ID, ACCEPT_PAYLOAD);
    }

    @Test
    void declineReturnsNoOrderAndReportsNextOffer() throws Exception {
        when(actionHandler.handle(SELLER_ID, BUYER_ID, DECLINE_PAYLOAD))
                .thenReturn(Optional.of(WaitlistOfferActionResult.declined(401L, 402L)));

        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(DECLINE_PAYLOAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("DECLINE"))
                .andExpect(jsonPath("$.data.waitlistEntryId").value(401))
                .andExpect(jsonPath("$.data.createdOrderId").isEmpty())
                .andExpect(jsonPath("$.data.nextOfferedEntryId").value(402));
    }

    @Test
    void malformedPayloadReturnsBadRequest() throws Exception {
        when(actionHandler.handle(SELLER_ID, BUYER_ID, "NOT_AN_ACTION"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("NOT_AN_ACTION")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_WAITLIST_ACTION_PAYLOAD"));
    }

    @Test
    void missingBuyerIdReturnsBadRequestBeforeHandler() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerPlatformId":" ","payload":"WAITLIST_ACCEPT:401"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verifyNoInteractions(actionHandler);
    }

    @Test
    void unreadableJsonReturnsBadRequestBeforeHandler() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(actionHandler);
    }

    @Test
    void wrongBuyerReturnsForbidden() throws Exception {
        when(actionHandler.handle(SELLER_ID, BUYER_ID, ACCEPT_PAYLOAD))
                .thenThrow(new WaitlistOfferNotAcceptableException(
                        "The responding buyer does not own this offer",
                        WaitlistOfferNotAcceptableException.Reason.WRONG_BUYER
                ));

        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ACCEPT_PAYLOAD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("WAITLIST_BUYER_MISMATCH"));
    }

    @Test
    void alreadyProcessedOfferReturnsConflict() throws Exception {
        when(actionHandler.handle(SELLER_ID, BUYER_ID, ACCEPT_PAYLOAD))
                .thenThrow(new WaitlistOfferNotAcceptableException(
                        "Waitlist entry does not have an active offer"
                ));

        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ACCEPT_PAYLOAD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("WAITLIST_OFFER_NOT_ACCEPTABLE"));
    }

    @Test
    void missingEntryReturnsNotFound() throws Exception {
        when(actionHandler.handle(SELLER_ID, BUYER_ID, ACCEPT_PAYLOAD))
                .thenThrow(new ResourceNotFoundException("Waitlist entry not found: 401"));

        mockMvc.perform(post(PATH)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ACCEPT_PAYLOAD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void missingAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ACCEPT_PAYLOAD)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(actionHandler);
    }

    @Test
    void invalidAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ACCEPT_PAYLOAD)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(actionHandler);
    }

    private String bearerToken() {
        return "Bearer " + jwtService.generateToken(SELLER_ID, "seller@test.com");
    }

    private String body(String payload) {
        return "{\"buyerPlatformId\":\"" + BUYER_ID
                + "\",\"payload\":\"" + payload + "\"}";
    }
}
