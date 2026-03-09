package vo;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
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
