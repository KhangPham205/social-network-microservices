package com.socialnetwork.common.dto;

/** Spring Data interface projection for "GROUP BY id, COUNT(*)" queries. */
public interface IdCount {
  String getId();

  Long getCount();
}
