package com.socialnetwork.media_service.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "react_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactType {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  private String charSymbol;
  private String iconUrl;
}
