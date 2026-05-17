package com.socialnetwork.moderation_service.client;

import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/posts")
public interface PostClient {
  @GetExchange("/{id}/owner-id")
  Long getPostOwnerId(@PathVariable("id") String id);

  @GetExchange("/comments/{id}/owner-id")
  Long getCommentOwnerId(@PathVariable("id") String id);

  @PostExchange("/api/v1/posts/batch")
  List<PostResponse> getPostsByIds(@RequestBody List<Long> ids);

  // Gọi sang Post Service lấy danh sách Comment
  @PostExchange("/api/v1/comments/batch")
  List<CommentResponse> getCommentsByIds(@RequestBody List<Long> ids);
}
