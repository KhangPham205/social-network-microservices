package com.socialnetwork.media_service.mapper;

import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.model.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommentMapper {

  @Mapping(source = "author.id", target = "authorId")
  @Mapping(source = "author.displayName", target = "authorName")
  @Mapping(source = "author.avatarUrl", target = "authorAvatar")
  @Mapping(source = "post.id", target = "postId")
  @Mapping(source = "parent.id", target = "parentId")
  @Mapping(target = "reactSummary", ignore = true)
  @Mapping(target = "childrenCount", ignore = true)
  CommentResponse toDto(Comment comment);
}
