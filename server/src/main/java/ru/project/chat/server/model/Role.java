package ru.project.chat.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Role {
    private int id;
    private RoleName name;
}
