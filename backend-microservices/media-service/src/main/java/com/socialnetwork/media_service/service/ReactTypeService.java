package com.socialnetwork.media_service.service;

import com.socialnetwork.media_service.model.ReactType;
import java.util.List;

public interface ReactTypeService {
  ReactType getById(Long id);

  List<ReactType> getAllTargetTypes();

  ReactType createReactType(ReactType reactType);

  ReactType updateReactType(ReactType reactType);

  void deleteReactType(ReactType reactType);

  void deleteReactTypeById(Long id);
}
