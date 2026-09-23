package com.socialnetwork.chat_service.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberId implements Serializable {

  private Long roomId;
  private Long userId;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RoomMemberId other)) {
      return false;
    }
    return Objects.equals(roomId, other.roomId) && Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roomId, userId);
  }

  @Override
  public String toString() {
    return roomId + "/" + userId;
  }
}
