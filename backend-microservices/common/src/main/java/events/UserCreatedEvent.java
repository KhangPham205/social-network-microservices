package events;

public record UserCreatedEvent(Long accountId, String username, String email) {}
