package com.socialnetwork.moderation_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** media-service internal API: post and comment ownership plus batch reads for the admin views. */
@HttpExchange(ApiConstants.MEDIA + ApiConstants.INTERNAL)
public interface MediaClient {

  @GetExchange("/posts/{id}/owner-id")
  Long getPostOwnerId(@PathVariable("id") String id);

  @PostExchange("/posts/batch")
  List<PostResponse> getPostsByIds(@RequestBody List<Long> ids);

  @GetExchange("/comments/{id}/owner-id")
  Long getCommentOwnerId(@PathVariable("id") String id);

  @PostExchange("/comments/batch")
  List<CommentResponse> getCommentsByIds(@RequestBody List<Long> ids);
}
