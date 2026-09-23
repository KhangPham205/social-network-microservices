package com.socialnetwork.media_service.mapper;

import com.socialnetwork.media_service.dto.post.PostResponse;
import com.socialnetwork.media_service.model.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Flat entity-to-DTO mapping. The shared post and the reaction summary are attached by
 * {@code PostServiceImpl}, which knows what the current viewer is allowed to see.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {

  @Mapping(source = "author.id", target = "authorId")
  @Mapping(source = "author.displayName", target = "authorName")
  @Mapping(source = "author.avatarUrl", target = "authorAvatar")
  @Mapping(source = "sharedPost.id", target = "sharedPostId")
  @Mapping(target = "sharedPost", ignore = true)
  @Mapping(target = "reactSummary", ignore = true)
  PostResponse toDto(Post post);
}
