package com.socialnetwork.media_service.config;

import com.socialnetwork.media_service.model.ReactType;
import com.socialnetwork.media_service.repository.ReactTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Populates {@code react_types} on first start so {@code toggleReact} has something to reference. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactTypeSeeder implements ApplicationRunner {

  static final List<ReactType> DEFAULT_TYPES =
      List.of(
          ReactType.builder().name("LIKE").charSymbol("👍").build(),
          ReactType.builder().name("LOVE").charSymbol("❤️").build(),
          ReactType.builder().name("HAHA").charSymbol("😂").build(),
          ReactType.builder().name("WOW").charSymbol("😮").build(),
          ReactType.builder().name("SAD").charSymbol("😢").build(),
          ReactType.builder().name("ANGRY").charSymbol("😡").build());

  private final ReactTypeRepository reactTypeRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (reactTypeRepository.count() > 0) {
      return;
    }
    reactTypeRepository.saveAll(DEFAULT_TYPES);
    log.info("Seeded {} default react types", DEFAULT_TYPES.size());
  }
}
