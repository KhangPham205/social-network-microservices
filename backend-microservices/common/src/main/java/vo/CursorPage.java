package vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CursorPage<T> {
  private List<T> content;
  private String nextCursor; // messageId (UUID string) of last element in this page, or null
}
