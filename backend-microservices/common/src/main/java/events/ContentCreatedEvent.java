package events;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentCreatedEvent {
  private Long targetId;
  private String targetType; // "POST" or "COMMENT"
  private String content;
  private Long authorId;
  private List<Map<String, String>> media;
}
