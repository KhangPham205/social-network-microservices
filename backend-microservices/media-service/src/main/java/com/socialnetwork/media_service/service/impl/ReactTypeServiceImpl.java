package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.media_service.model.ReactType;
import com.socialnetwork.media_service.repository.ReactTypeRepository;
import com.socialnetwork.media_service.service.ReactTypeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReactTypeServiceImpl implements ReactTypeService {

  private final ReactTypeRepository reactTypeRepository;

  @Override
  public ReactType getById(Long id) {
    return reactTypeRepository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("ReactType not found"));
  }

  @Override
  public List<ReactType> getAllTargetTypes() {
    return reactTypeRepository.findAll();
  }

  @Override
  public ReactType createReactType(ReactType reactType) {
    return reactTypeRepository.save(reactType);
  }

  @Override
  public ReactType updateReactType(ReactType reactType) {
    return reactTypeRepository.save(reactType);
  }

  @Override
  public void deleteReactType(ReactType reactType) {
    reactTypeRepository.delete(reactType);
  }

  @Override
  public void deleteReactTypeById(Long id) {
    ReactType reactType = getById(id);
    reactTypeRepository.delete(reactType);
  }
}
