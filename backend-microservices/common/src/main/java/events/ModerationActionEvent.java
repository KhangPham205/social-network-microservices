package events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationActionEvent {
  private String targetId;
  private String targetType;
  private String action; // e.g., "BLOCK"
}
