package com.alertops.webhook.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.alertops.task.service.TaskService;
import com.alertops.webhook.dto.CreateWebhookRequest;
import com.alertops.webhook.dto.UpdateWebhookRequest;
import com.alertops.webhook.dto.WebhookConfigurationResponse;
import com.alertops.webhook.dto.WebhookEventResponse;
import com.alertops.webhook.dto.WebhookTriggerResponse;
import com.alertops.webhook.exception.WebhookException;
import com.alertops.webhook.model.WebhookConfiguration;
import com.alertops.webhook.model.WebhookEvent;
import com.alertops.webhook.repository.WebhookConfigurationRepository;
import com.alertops.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WebhookService {
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookConfigurationRepository configurationRepository;
    private final WebhookEventRepository eventRepository;
    private final FlowRepository flowRepository;
    private final TaskRepository taskRepository;
    private final EscalationRepository escalationRepository;
    private final EscalationService escalationService;
    private final FlowExecutionStateService flowExecutionStateService;
    private final StartFlowExecutionUseCase startFlowExecutionUseCase;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Clock clock;
    private final int maxPayloadBytes;
    private final int rateLimit;
    private final ConcurrentHashMap<UUID, RequestWindow> requestWindows = new ConcurrentHashMap<>();

    // Creates the webhook service with persistence, execution, rate-limit, and clock dependencies.
    public WebhookService(
            WebhookConfigurationRepository configurationRepository,
            WebhookEventRepository eventRepository,
            FlowRepository flowRepository,
            TaskRepository taskRepository,
            EscalationRepository escalationRepository,
            EscalationService escalationService,
            FlowExecutionStateService flowExecutionStateService,
            StartFlowExecutionUseCase startFlowExecutionUseCase,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            Clock clock,
            @Value("${alertops.webhook.max-body-bytes:65536}") int maxPayloadBytes,
            @Value("${alertops.webhook.rate-limit:120}") int rateLimit) {
        this.configurationRepository = configurationRepository;
        this.eventRepository = eventRepository;
        this.flowRepository = flowRepository;
        this.taskRepository = taskRepository;
        this.escalationRepository = escalationRepository;
        this.escalationService = escalationService;
        this.flowExecutionStateService = flowExecutionStateService;
        this.startFlowExecutionUseCase = startFlowExecutionUseCase;
        this.objectMapper = objectMapper;
        this.redisTemplateProvider = redisTemplateProvider;
        this.clock = clock;
        this.maxPayloadBytes = maxPayloadBytes;
        this.rateLimit = rateLimit;
    }

    @Transactional
    public WebhookConfigurationResponse createWebhook(CreateWebhookRequest request) {
        AuthContext context = requireAdministrator();
        if (request == null || blank(request.getName()) || request.getName().trim().length() > 120
                || request.getFlowId() == null) {
            throw WebhookException.badRequest("Webhook name and default flow are required.");
        }
        requireTeamFlow(request.getFlowId(), context.getTeamId());

        String secret = generateSecret();
        WebhookConfiguration configuration = new WebhookConfiguration();
        configuration.setTeamId(context.getTeamId());
        configuration.setDefaultFlowId(request.getFlowId());
        configuration.setName(request.getName().trim());
        configuration.setSecretHash(hash(secret));
        return WebhookConfigurationResponse.from(configurationRepository.save(configuration), secret);
    }

    @Transactional(readOnly = true)
    public List<WebhookConfigurationResponse> listWebhooks() {
        UUID teamId = requireContext().getTeamId();
        return configurationRepository.findAllByTeamIdOrderByCreatedAtDesc(teamId).stream()
                .map(configuration -> WebhookConfigurationResponse.from(configuration, null))
                .toList();
    }

    @Transactional
    public WebhookConfigurationResponse rotateSecret(UUID webhookId) {
        UUID teamId = requireAdministrator().getTeamId();
        WebhookConfiguration configuration = configurationRepository.findByIdAndTeamId(webhookId, teamId)
                .orElseThrow(WebhookException::notFound);
        String secret = generateSecret();
        configuration.setSecretHash(hash(secret));
        return WebhookConfigurationResponse.from(configurationRepository.save(configuration), secret);
    }

    @Transactional
    public WebhookConfigurationResponse updateWebhook(UUID webhookId, UpdateWebhookRequest request) {
        UUID teamId = requireAdministrator().getTeamId();
        if (request == null || request.getEnabled() == null) {
            throw WebhookException.badRequest("Enabled is required.");
        }
        WebhookConfiguration configuration = configurationRepository.findByIdAndTeamId(webhookId, teamId)
                .orElseThrow(WebhookException::notFound);
        configuration.setEnabled(request.getEnabled());
        return WebhookConfigurationResponse.from(configurationRepository.save(configuration), null);
    }

    // Accepts a signed webhook event and atomically creates its task, escalation, and event record.
    @Transactional
    public WebhookTriggerResponse receiveEvent(UUID webhookId, String secret, JsonNode payload) {
        if (blank(secret)) {
            throw WebhookException.unauthorized();
        }
        WebhookConfiguration configuration = configurationRepository.findByIdForUpdate(webhookId)
                .orElseThrow(WebhookException::notFound);
        if (!configuration.isEnabled() || !MessageDigest.isEqual(
                hash(secret.trim()).getBytes(StandardCharsets.US_ASCII),
                configuration.getSecretHash().getBytes(StandardCharsets.US_ASCII))) {
            throw WebhookException.unauthorized();
        }
        enforceRateLimit(webhookId);
        if (payload == null || !payload.isObject()) {
            throw WebhookException.badRequest("Webhook body must be a JSON object.");
        }
        String serializedPayload = serializePayload(payload);
        if (serializedPayload.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes) {
            throw WebhookException.tooLarge();
        }
        String payloadHash = hash(serializedPayload);
        String eventId = requiredText(payload, "eventId", 255);

        WebhookEvent existing = eventRepository.findByWebhookIdAndEventId(webhookId, eventId.trim()).orElse(null);
        // Reusing an event ID must not create a second task or escalation.
        if (existing != null) {
            // The same ID with different data is an invalid retry.
            if (!existing.getPayloadHash().equals(payloadHash)) {
                throw WebhookException.conflict("This event ID was already used with a different payload.");
            }
            // The same ID and data is a safe retry; return the original records.
            Escalation escalation = escalationRepository.findById(existing.getEscalationId()).orElseThrow(WebhookException::notFound);
            return new WebhookTriggerResponse(webhookId, existing.getEventId(), escalation.getFlowId(),
                    existing.getTaskId(), existing.getEscalationId(), true);
        }

        String taskName = requiredText(payload, "taskName", TaskService.MAX_TASK_NAME_LENGTH);
        String description = requiredText(payload, "description", TaskService.MAX_TASK_DESCRIPTION_LENGTH);
        String source = requiredText(payload, "source", TaskService.MAX_TASK_SOURCE_LENGTH);
        String priority = optionalText(payload, "priority", TaskService.MAX_TASK_PRIORITY_LENGTH);
        String category = optionalText(payload, "category", TaskService.MAX_TASK_CATEGORY_LENGTH);
        String referenceUrl = optionalText(payload, "referenceUrl", TaskService.MAX_TASK_REFERENCE_URL_LENGTH);
        if (referenceUrl != null && !HTTP_URL.matcher(referenceUrl).matches()) {
            throw WebhookException.badRequest("Reference URL must be a valid HTTP(S) URL.");
        }

        UUID flowId = flowIdFromPayload(payload, configuration.getDefaultFlowId());
        requireTeamFlow(flowId, configuration.getTeamId());

        Task task = new Task();
        task.setName(taskName);
        task.setDescription(description);
        task.setSource(source);
        task.setPriority(priority);
        task.setCategory(category);
        task.setReferenceUrl(referenceUrl);
        task.setTeamId(configuration.getTeamId());
        Task savedTask = taskRepository.saveAndFlush(task);

        Escalation savedEscalation = escalationService.createEscalationForTeam(
                taskName, savedTask.getId(), flowId, configuration.getTeamId());

        startFlowExecutionUseCase.executeForTeam(flowExecutionStateService, savedEscalation.getId(), configuration.getTeamId());

        WebhookEvent event = new WebhookEvent();
        event.setWebhookId(webhookId);
        event.setEventId(eventId.trim());
        event.setReceivedAt(Instant.now(clock));
        event.setPayload(payload);
        event.setPayloadHash(payloadHash);
        event.setTaskId(savedTask.getId());
        event.setEscalationId(savedEscalation.getId());
        eventRepository.save(event);

        configuration.setLastTriggeredAt(event.getReceivedAt());
        configurationRepository.save(configuration);
        return new WebhookTriggerResponse(webhookId, event.getEventId(), flowId, savedTask.getId(),
                savedEscalation.getId(), false);
    }

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> listEvents(UUID webhookId) {
        UUID teamId = requireContext().getTeamId();
        requireConfiguration(webhookId, teamId);
        return eventRepository.findAllByWebhookIdOrderByReceivedAtDesc(webhookId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookEventResponse getEvent(UUID webhookId, UUID eventRecordId) {
        UUID teamId = requireContext().getTeamId();
        requireConfiguration(webhookId, teamId);
        return eventRepository.findByIdAndWebhookId(eventRecordId, webhookId)
                .map(this::toResponse)
                .orElseThrow(WebhookException::notFound);
    }

    private WebhookEventResponse toResponse(WebhookEvent event) {
        return new WebhookEventResponse(event.getId(), event.getWebhookId(), event.getEventId(),
                event.getReceivedAt(), event.getPayload(), event.getTaskId(), event.getEscalationId());
    }

    private WebhookConfiguration requireConfiguration(UUID webhookId, UUID teamId) {
        return configurationRepository.findByIdAndTeamId(webhookId, teamId).orElseThrow(WebhookException::notFound);
    }

    private UUID flowIdFromPayload(JsonNode payload, UUID defaultFlowId) {
        JsonNode flowNode = payload.get("flowId");
        if (flowNode == null || flowNode.isNull() || blank(flowNode.asText())) {
            return defaultFlowId;
        }
        try {
            return UUID.fromString(flowNode.asText().trim());
        } catch (IllegalArgumentException e) {
            throw WebhookException.badRequest("flowId must be a valid UUID.");
        }
    }

    private void requireTeamFlow(UUID flowId, UUID teamId) {
        Flow flow = flowRepository.findByIdAndTeamId(flowId, teamId);
        if (flow == null) {
            throw WebhookException.badRequest("The selected response path does not belong to this team.");
        }
    }

    private AuthContext requireAdministrator() {
        AuthContext context = requireContext();
        if (!"TEAM_OWNER".equals(context.getRole()) && !"ADMIN".equals(context.getRole())) {
            throw WebhookException.forbidden("Only team owners and admins can manage webhooks.");
        }
        return context;
    }

    private AuthContext requireContext() {
        AuthContext context = AuthContextHolder.get();
        if (context == null || context.getTeamId() == null) {
            throw WebhookException.forbidden("Select a team before using webhooks.");
        }
        return context;
    }

    private String requiredText(JsonNode payload, String field, int maxLength) {
        String value = optionalText(payload, field, maxLength);
        if (value == null) {
            throw WebhookException.badRequest(field + " is required.");
        }
        return value;
    }

    private String optionalText(JsonNode payload, String field, int maxLength) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw WebhookException.badRequest(field + " must be text.");
        }
        String value = node.asText().trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw WebhookException.badRequest(field + " is too long.");
        }
        return value;
    }

    private String serializePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw WebhookException.badRequest("Webhook body could not be read.");
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    // Enforces the per-webhook request budget using Redis or an explicit local fallback.
    private void enforceRateLimit(UUID webhookId) {
        // Use Redis so all application instances share one counter.
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                // Put each webhook request into its current one-minute bucket.
                long minute = clock.millis() / 60_000;
                String key = "alertops:webhook-rate:" + webhookId + ":" + minute;
                // Redis increments this counter safely when requests arrive together.
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1) {
                    // Remove the bucket shortly after the minute ends.
                    redisTemplate.expire(key, java.time.Duration.ofSeconds(65));
                }
                if (count != null && count > rateLimit) {
                    // Stop this webhook after it exceeds the configured limit.
                    throw WebhookException.rateLimited();
                }
                return;
            } catch (WebhookException e) {
                throw e;
            } catch (RuntimeException ignored) {
                // Fall back to a local counter when Redis is unavailable.
            }
        }
        // This fallback is per application instance, so Redis is preferred in production.
        long now = clock.millis();
        RequestWindow window = requestWindows.compute(webhookId, (key, current) -> {
            if (current == null || now - current.startedAt > 60_000) {
                return new RequestWindow(now, new AtomicLong(1));
            }
            current.count.incrementAndGet();
            return current;
        });
        if (window.count.get() > rateLimit) {
            throw WebhookException.rateLimited();
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record RequestWindow(long startedAt, AtomicLong count) {}
}
