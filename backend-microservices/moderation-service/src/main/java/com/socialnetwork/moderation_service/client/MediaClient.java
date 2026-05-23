package com.socialnetwork.moderation_service.client;

import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/media")
public interface MediaClient {

  @GetExchange("/posts/{id}/owner-id")
  Long getPostOwnerId(@PathVariable("id") String id);

  @GetExchange("/comments/{id}/owner-id")
  Long getCommentOwnerId(@PathVariable("id") String id);

  @PostExchange("/posts/batch")
  List<PostResponse> getPostsByIds(@RequestBody List<Long> ids);

  @PostExchange("/comments/batch")
  List<CommentResponse> getCommentsByIds(@RequestBody List<Long> ids);
}