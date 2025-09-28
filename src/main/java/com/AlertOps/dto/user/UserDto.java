package com.AlertOps.dto.user;

import com.AlertOps.dto.Escalation.EscalationResDto;
import com.AlertOps.model.Escalation;
import com.AlertOps.model.Role;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;


@Getter
@Setter
public class UserDto {
    private  String name;
    private  String email;
    private  String userName;
    private Long id;
   // private List<EscalationResDto> escalations;
    private Set<Role> roles;
   // private
}
