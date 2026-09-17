package events;

public record ProfileFailedEvent(Long accountId, String reason) {}
