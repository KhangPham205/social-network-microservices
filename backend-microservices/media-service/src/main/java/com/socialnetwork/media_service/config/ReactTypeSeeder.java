package com.socialnetwork.media_service.config;

import com.socialnetwork.media_service.model.ReactType;
import com.socialnetwork.media_service.repository.ReactTypeRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Populates {@code react_types} on first start so {@code toggleReact} has something to reference.
 * Entities are rebuilt on every run so the seeder never reuses already persisted instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactTypeSeeder implements ApplicationRunner {

  static final Map<String, String> DEFAULT_TYPES =
      new java.util.LinkedHashMap<>(
          Map.of(
              "LIKE", "👍",
              "LOVE", "❤️",
              "HAHA", "😂",
              "WOW", "😮",
              "SAD", "😢",
              "ANGRY", "😡"));

  private final ReactTypeRepository reactTypeRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (reactTypeRepository.count() > 0) {
      return;
    }
    List<ReactType> seeds =
        DEFAULT_TYPES.entrySet().stream()
            .map(
                entry ->
                    ReactType.builder().name(entry.getKey()).charSymbol(entry.getValue()).build())
            .toList();
    reactTypeRepository.saveAll(seeds);
    log.info("Seeded {} default react types", seeds.size());
  }
}
