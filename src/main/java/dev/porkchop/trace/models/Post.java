package dev.porkchop.trace.models;

import lombok.Data;

@Data
public class Post {
    private Integer id;
    private String title;
    private String body;
    private Integer userId;
}
