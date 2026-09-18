package com.socialnetwork.common.vo;

import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/** Stable, framework-independent page envelope returned by every paged endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
  private int numberOfElements;
  private List<T> content;

  public static <T> PageVO<T> from(Page<T> page) {
    return PageVO.<T>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(page.getNumberOfElements())
        .content(page.getContent())
        .build();
  }

  public static <S, T> PageVO<T> from(Page<S> page, Function<S, T> mapper) {
    return from(page.map(mapper));
  }

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
