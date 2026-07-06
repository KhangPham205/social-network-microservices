package service;

import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vo.PageVO;

@Transactional
public abstract class BaseFilterService<T, D> {

  /**
   * Lọc, phân trang và chuyển sang DTO cho bất kỳ entity nào. Hỗ trợ filter RSQL + baseSpec logic
   * chung.
   */
  public PageVO<D> filterEntity(
      Class<T> entityClass,
      String filter,
      Pageable pageable,
      JpaSpecificationExecutor<T> repository,
      Function<T, D> mapper,
      Specification<T> baseSpec) {
    // Gộp baseSpec (logic cố định) và spec từ RSQL
    Specification<T> rsqlSpec = RSQLJPASupport.toSpecification(filter);
    Specification<T> finalSpec = (baseSpec != null) ? baseSpec.and(rsqlSpec) : rsqlSpec;

    Page<T> page = repository.findAll(finalSpec, pageable);

    List<D> content = page.getContent().stream().map(mapper).toList();

    return PageVO.<D>builder()
        .page(pageable.getPageNumber())
        .size(pageable.getPageSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(page.getNumberOfElements())
        .content(content)
        .build();
  }

  protected PageVO<D> filterEntity(
      Class<T> entityClass,
      String filter,
      Pageable pageable,
      JpaSpecificationExecutor<T> repository,
      Function<T, D> mapper) {
    return filterEntity(entityClass, filter, pageable, repository, mapper, null);
  }
}
