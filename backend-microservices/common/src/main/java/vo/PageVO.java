package vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
  Integer page;
  private Integer size;
  private Long totalElements;
  private Integer totalPages;
  private Integer numberOfElements;
  private List<T> content;

  public static <T> PageVO<T> emptyPage(Page<?> page) {
    return PageVO.<T>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(0)
        .content(List.of())
        .build();
  }
}
