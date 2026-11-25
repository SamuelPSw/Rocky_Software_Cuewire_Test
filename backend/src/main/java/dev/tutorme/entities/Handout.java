package dev.tutorme.entities;

import jakarta.persistence.*;

@Entity
public class Handout {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public long size;
    public Boolean lessonNote;
    @Lob
    public byte[] content;
}
