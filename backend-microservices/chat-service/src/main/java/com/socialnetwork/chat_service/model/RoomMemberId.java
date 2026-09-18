package com.socialnetwork.chat_service.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberId implements Serializable {
  private Long roomId;
  private Long userId;
}
