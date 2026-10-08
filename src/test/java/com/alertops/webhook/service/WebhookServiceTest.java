package com.alertops.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.alertops.flow.model.Flow;
import com.alertops.flow.repository.FlowRepository;
import com.alertops.flow_execution_engine.application.StartFlowExecutionUseCase;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.service.EscalationService;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.model.Task;
import com.alertops.task.repository.TaskRepository;
import com.alertops.webhook.exception.WebhookException;
import com.alertops.webhook.model.WebhookConfiguration;
import com.alertops.webhook.model.WebhookEvent;
import com.alertops.webhook.repository.WebhookConfigurationRepository;
import com.alertops.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class WebhookServiceTest {
    private static final UUID TEAM_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID WEBHOOK_ID = UUID.fromString("51000000-0000-0000-0000-000000000002");
    private static final UUID FLOW_ID = UUID.fromString("51000000-0000-0000-0000-000000000003");
    private static final UUID TASK_ID = UUID.fromString("51000000-0000-0000-0000-000000000004");
    private static final UUID ESCALATION_ID = UUID.fromString("51000000-0000-0000-0000-000000000005");
    private static final String SECRET = "webhook-secret";
    private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");

    private final WebhookConfigurationRepository configurationRepository = mock(WebhookConfigurationRepository.class);
    private final WebhookEventRepository eventRepository = mock(WebhookEventRepository.class);
    private final FlowRepository flowRepository = mock(FlowRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final EscalationRepository escalationRepository = mock(EscalationRepository.class);
    private final EscalationService escalationService = mock(EscalationService.class);
    private final FlowExecutionStateService flowExecutionStateService = mock(FlowExecutionStateService.class);
    private final StartFlowExecutionUseCase startFlowExecutionUseCase = mock(StartFlowExecutionUseCase.class);
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider = mock(ObjectProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebhookConfiguration configuration = new WebhookConfiguration();
    private final Task savedTask = mock(Task.class);
    private final Escalation savedEscalation = mock(Escalation.class);
    private WebhookService webhookService;

    // Builds a webhook service with a fixed clock and disabled Redis fallback.
    @BeforeEach
    void setUp() {
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), TEAM_ID, "TEAM_OWNER", "token", "owner@example.com"));
        configuration.setTeamId(TEAM_ID);
        configuration.setDefaultFlowId(FLOW_ID);
        configuration.setEnabled(true);
        configuration.setSecretHash(hash(SECRET));
        when(configurationRepository.findByIdForUpdate(WEBHOOK_ID)).thenReturn(Optional.of(configuration));
        when(eventRepository.findByWebhookIdAndEventId(eq(WEBHOOK_ID), anyString())).thenReturn(Optional.empty());
        when(flowRepository.findByIdAndTeamId(any(), eq(TEAM_ID))).thenReturn(new Flow());
        when(savedTask.getId()).thenReturn(TASK_ID);
        when(taskRepository.saveAndFlush(any(Task.class))).thenReturn(savedTask);
        when(savedEscalation.getId()).thenReturn(ESCALATION_ID);
        when(savedEscalation.getFlowId()).thenReturn(FLOW_ID);
        when(escalationService.createEscalationForTeam(anyString(), eq(TASK_ID), eq(FLOW_ID), eq(TEAM_ID)))
                .thenReturn(savedEscalation);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        when(configurationRepository.save(any(WebhookConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        webhookService = newWebhookService(65_536, 120);
    }

    // Clears the webhook service authentication context after each test.
    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    // Preserves validated task metadata and links the created event to both records.
    @Test
    void createsTaskEscalationAndEventWithWebhookMetadata() throws Exception {
        ObjectNode payload = validPayload("event-1");
        payload.put("priority", "P1");
        payload.put("category", "Database");
        payload.put("referenceUrl", "https://status.example.com/incidents/1");

        var response = webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload);

        assertThat(response.replayed()).isFalse();
        assertThat(response.taskId()).isEqualTo(TASK_ID);
        assertThat(response.escalationId()).isEqualTo(ESCALATION_ID);
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).saveAndFlush(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getName()).isEqualTo("Database outage");
        assertThat(taskCaptor.getValue().getDescription()).isEqualTo("Primary database is unavailable");
        assertThat(taskCaptor.getValue().getSource()).isEqualTo("monitoring");
        assertThat(taskCaptor.getValue().getPriority()).isEqualTo("P1");
        assertThat(taskCaptor.getValue().getCategory()).isEqualTo("Database");
        assertThat(taskCaptor.getValue().getReferenceUrl()).isEqualTo("https://status.example.com/incidents/1");
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPayload()).isEqualTo(payload);
        assertThat(eventCaptor.getValue().getReceivedAt()).isEqualTo(NOW);
        verify(startFlowExecutionUseCase).executeForTeam(flowExecutionStateService, ESCALATION_ID, TEAM_ID);
    }

    // Rejects a webhook description that exceeds the manual task limit before persistence.
    @Test
    void rejectsDescriptionOverSharedTaskLimit() throws Exception {
        ObjectNode payload = validPayload("event-2");
        payload.put("description", "x".repeat(1001));

        assertStatus(400, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload));

        verifyNoInteractions(taskRepository, escalationService, startFlowExecutionUseCase);
        verify(eventRepository, never()).save(any(WebhookEvent.class));
    }

    // Rejects a webhook event when a required task field is missing.
    @Test
    void rejectsMissingRequiredTaskField() throws Exception {
        ObjectNode payload = validPayload("event-2-missing");
        payload.remove("source");

        assertStatus(400, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload));

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    // Rejects an optional task field when its value exceeds the shared limit.
    @Test
    void rejectsOversizedOptionalPriority() throws Exception {
        ObjectNode payload = validPayload("event-2-priority");
        payload.put("priority", "x".repeat(21));

        assertStatus(400, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload));

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    // Rejects an optional reference URL unless it uses the supported HTTP(S) scheme.
    @Test
    void rejectsInvalidReferenceUrl() throws Exception {
        ObjectNode payload = validPayload("event-2-url");
        payload.put("referenceUrl", "ftp://status.example.com/incident/1");

        assertStatus(400, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload));

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
    }

    // Rejects a serialized payload that exceeds the service limit even without a request header.
    @Test
    void rejectsPayloadOverConfiguredSizeLimit() throws Exception {
        webhookService = newWebhookService(100, 120);

        assertStatus(413, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, validPayload("event-2-size")));

        verifyNoInteractions(eventRepository, taskRepository, escalationService, startFlowExecutionUseCase);
    }

    // Rejects a foreign flow before creating a task for the webhook team.
    @Test
    void rejectsFlowFromAnotherTeam() throws Exception {
        ObjectNode payload = validPayload("event-3");
        UUID foreignFlowId = UUID.randomUUID();
        payload.put("flowId", foreignFlowId.toString());
        when(flowRepository.findByIdAndTeamId(foreignFlowId, TEAM_ID)).thenReturn(null);

        assertStatus(400, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload));

        verify(taskRepository, never()).saveAndFlush(any(Task.class));
        verify(escalationService, never()).createEscalationForTeam(anyString(), any(), any(), any());
    }

    // Uses a valid same-team flow override instead of the webhook default flow.
    @Test
    void acceptsSameTeamFlowOverride() throws Exception {
        UUID overrideFlowId = UUID.fromString("51000000-0000-0000-0000-000000000006");
        ObjectNode payload = validPayload("event-3-override");
        payload.put("flowId", overrideFlowId.toString());
        when(escalationService.createEscalationForTeam(anyString(), eq(TASK_ID), eq(overrideFlowId), eq(TEAM_ID)))
                .thenReturn(savedEscalation);

        var response = webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload);

        assertThat(response.flowId()).isEqualTo(overrideFlowId);
        verify(flowRepository).findByIdAndTeamId(overrideFlowId, TEAM_ID);
        verify(escalationService).createEscalationForTeam(anyString(), eq(TASK_ID), eq(overrideFlowId), eq(TEAM_ID));
    }

    // Returns the original records for an identical retry without creating duplicates.
    @Test
    void replaysIdenticalEventWithoutCreatingAnotherTask() throws Exception {
        ObjectNode payload = validPayload("event-4");
        WebhookEvent existingEvent = existingEvent(payload);
        Escalation existingEscalation = mock(Escalation.class);
        when(existingEscalation.getFlowId()).thenReturn(FLOW_ID);
        when(eventRepository.findByWebhookIdAndEventId(WEBHOOK_ID, "event-4"))
                .thenReturn(Optional.of(existingEvent));
        when(escalationRepository.findById(ESCALATION_ID)).thenReturn(Optional.of(existingEscalation));

        var response = webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload);

        assertThat(response.replayed()).isTrue();
        assertThat(response.taskId()).isEqualTo(TASK_ID);
        verifyNoInteractions(taskRepository, escalationService, startFlowExecutionUseCase);
        verify(eventRepository, never()).save(any(WebhookEvent.class));
    }

    // Rejects reuse of an event ID when the retry payload has changed.
    @Test
    void rejectsChangedPayloadForExistingEvent() throws Exception {
        ObjectNode payload = validPayload("event-5");
        WebhookEvent existingEvent = existingEvent(payload);
        existingEvent.setPayloadHash(hash("different-payload"));
        when(eventRepository.findByWebhookIdAndEventId(WEBHOOK_ID, "event-5"))
                .thenReturn(Optional.of(existingEvent));

        assertStatus(409, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, payload));

        verifyNoInteractions(taskRepository, escalationService, startFlowExecutionUseCase, escalationRepository);
    }

    // Rejects disabled webhooks and accepts a newly rotated secret for enabled ones.
    @Test
    void rotatesSecretAndRejectsDisabledWebhook() throws Exception {
        when(configurationRepository.findByIdAndTeamId(WEBHOOK_ID, TEAM_ID)).thenReturn(Optional.of(configuration));
        var rotated = webhookService.rotateSecret(WEBHOOK_ID);

        assertThat(rotated.secret()).isNotBlank().isNotEqualTo(SECRET);
        verify(configurationRepository).save(configuration);

        configuration.setSecretHash(hash(SECRET));
        configuration.setEnabled(false);
        assertStatus(401, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, validPayload("event-6")));
        verifyNoInteractions(taskRepository, escalationService, startFlowExecutionUseCase);
    }

    // Stops new webhook events after the configured local fallback budget is exceeded.
    @Test
    void rateLimitsRequestsWhenRedisIsUnavailable() throws Exception {
        webhookService = newWebhookService(65_536, 1);

        webhookService.receiveEvent(WEBHOOK_ID, SECRET, validPayload("event-7"));

        assertStatus(429, () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, validPayload("event-8")));
    }

    // Does not save an event when escalation creation fails inside the transaction boundary.
    @Test
    void doesNotSaveEventWhenEscalationCreationFails() throws Exception {
        doThrow(new RuntimeException("database failure"))
                .when(escalationService).createEscalationForTeam(anyString(), eq(TASK_ID), eq(FLOW_ID), eq(TEAM_ID));

        assertThrows(RuntimeException.class,
                () -> webhookService.receiveEvent(WEBHOOK_ID, SECRET, validPayload("event-9")));

        verify(eventRepository, never()).save(any(WebhookEvent.class));
        verify(startFlowExecutionUseCase, never()).executeForTeam(any(), any(), any());
    }

    // Builds the smallest valid webhook payload shared by the behavior tests.
    private ObjectNode validPayload(String eventId) {
        return objectMapper.createObjectNode()
                .put("eventId", eventId)
                .put("taskName", "Database outage")
                .put("description", "Primary database is unavailable")
                .put("source", "monitoring");
    }

    // Creates a stored event whose hash matches the supplied JSON payload.
    private WebhookEvent existingEvent(ObjectNode payload) throws Exception {
        WebhookEvent event = new WebhookEvent();
        event.setWebhookId(WEBHOOK_ID);
        event.setEventId(payload.get("eventId").asText());
        event.setPayloadHash(hash(objectMapper.writeValueAsString(payload)));
        event.setTaskId(TASK_ID);
        event.setEscalationId(ESCALATION_ID);
        return event;
    }

    // Creates a webhook service with the requested payload and rate limits.
    private WebhookService newWebhookService(int maxPayloadBytes, int rateLimit) {
        return new WebhookService(configurationRepository, eventRepository, flowRepository, taskRepository,
                escalationRepository, escalationService, flowExecutionStateService, startFlowExecutionUseCase,
                objectMapper, redisTemplateProvider, Clock.fixed(NOW, ZoneOffset.UTC), maxPayloadBytes, rateLimit);
    }

    // Asserts the HTTP status represented by a webhook validation exception.
    private void assertStatus(int expectedStatus, ThrowingOperation operation) {
        WebhookException exception = assertThrows(WebhookException.class, operation::run);
        assertThat(exception.getStatus().value()).isEqualTo(expectedStatus);
    }

    // Hashes values the same way the production service hashes secrets and payloads.
    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        // Runs an operation that may throw a checked exception from test setup.
        void run() throws Exception;
    }
}
