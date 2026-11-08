package br.com.srm.creditengine.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
public class Assignor {

  @Id private Long id;
  private String name;
  private String document;

  protected Assignor() {}

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDocument() {
    return document;
  }
}
