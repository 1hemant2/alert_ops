package com.alertops.auth.service;

import com.alertops.security.JwtUtil;
import com.alertops.auth.dto.UserLoginDto;
import com.alertops.auth.dto.UserRegisterDto;
import com.alertops.auth.repository.UserRepository;
import com.alertops.auth.model.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
//import com.alertops.auth.component.AppConfig;
//import com.alertops.repository.RoleRepository;
//import com.alertops.team.repository.RoleRepository;
//import com.alertops.model.Escalation;
//import com.alertops.repository.UserRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.slf4j.// logger;
//import org.slf4j.// logger;
//import org.slf4j.// loggerFactory;

import java.util.*;

//@Slf4j
@Service
public class UserService {

    //private static final // logger // log = // loggerFactory.get// logger(UserService.class);
    private final UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private  JwtUtil jwtUtil;
//    private  final RoleRepository roleRepository;
//    private final AppConfig appConfig;


    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil
                       ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public User createUser(UserRegisterDto userObj) {
        try {
            User user = new User();
            user.setEmail(userObj.getEmail());
            user.setName(userObj.getName());
            user.setPassword(passwordEncoder.encode(userObj.getPassword()));
            return userRepository.save(user);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> Login(UserLoginDto userObj) {
        try {
            UsernamePasswordAuthenticationToken authInputToken = new UsernamePasswordAuthenticationToken(userObj.getEmail(), userObj.getPassword());
            System.out.println("AUTH PASSED");
            User user = userRepository.findByEmail(userObj.getEmail());
            Map<String, Object> obj = new HashMap<> ();
            obj.put("email", userObj.getEmail());

            authenticationManager.authenticate(authInputToken);
            String token = jwtUtil.generateToken(
                                 user.getId().toString(),
                                 obj, 
                                 6000
                            );
            return Map.of("jwt-token", token);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean deleteUser(Long id, String email) {
        try {
            boolean delted = false;
            if(id != null) {// findById returns Optional<User>
                userRepository.deleteById(id); // return null if not found
                delted = true;
            }
            if(email != null) {
                userRepository.deleteByEmail(email);
                delted = true;
            }
            return delted;
        } catch (Exception e) {
            // log.error("❌ Error while Fetching user user", e);
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

    public void updateUserEmail(String email, String newEmail) {
        try {
            if(email != null) {
                int updatedRows = userRepository.updateUserEmailByEmail(email, newEmail);
                // log.info("number of row update date successfully: {}", updatedRows);
            }
        } catch (Exception e) {
            // log.error("❌ Error while Fetching user user", e);
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

/*   public Set<String> getUserPermissions(String username) {
        User user = loadUserByUsername(username);
        System.out.println("user + ->> " + user.getRoles());
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }*/


/*    public User createUser(User user) {
        try {
            String defaultRole = appConfig.getDefaultRole();
            Role role = roleRepository.findByName(defaultRole);
            user.getRoles().add(role);
            User saved = userRepository.save(user);
            // log.info("✅ User created successfully: {}", saved);
            return saved;
        } catch (Exception e) {
            // log.error("❌ Error while creating user: {}", user, e);
            throw new RuntimeException("Failed to create user", e);
        }
    }*/

    public List<User> getAllUser() {
        try {
            List<User> users = userRepository.findAll();
            // log.info("✅ ser fetched successfully");
            return  users;
        } catch (Exception e) {
            // log.error("❌ Error while Fetching all user", e);
            throw new RuntimeException("Failed to fetch all user", e);
        }
    }

 /*   public UserDto getUser(Long id, String userName, String email) {
        try {
            UserDto userRes = new UserDto();
            if(id != null) {// findById returns Optional<User>
                System.out.println("reached before breaking");
                 User user = userRepository.findById(id)
                        .orElse(null);
                userRes.setName(user.getName());
                userRes.setRoles(user.getRoles());
                userRes.setId(user.getId());
                userRes.setUserName(user.getUserName());
            }
            if(userName != null) {
                User user = userRepository.findByUserName(userName);
                userRes.setName(user.getName());
                userRes.setRoles(user.getRoles());
                userRes.setId(user.getId());
                userRes.setUserName(user.getUserName());
            }
            if(email != null) {
                User user =  userRepository.findByEmail(email);
                userRes.setName(user.getName());
                userRes.setRoles(user.getRoles());
                userRes.setId(user.getId());
                userRes.setUserName(user.getUserName());
            }
            return  userRes;
        } catch (Exception e) {
            // log.error("❌ Error while Fetching user user", e);
            throw new RuntimeException("Failed to fetch user", e);
        }
    }
*/


/*    public  Set<Role> getUserRole(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow();
            return user.getRoles();
        } catch (RuntimeException e) {
            // log.error("❌ Error while Fetching user role", e);
            throw new RuntimeException("Failed to fetch user role", e);
        }
    }*/

/*    public void addUserRole(Long userId, String newRole) {
        try{
            Role role = roleRepository.findByName(newRole);
            userRepository.findById(userId).ifPresent(u -> {
                u.getRoles().add(role);
                userRepository.save(u);
            });
        } catch (RuntimeException e) {
            // log.error("❌ Error while updating user role", e);
            throw new RuntimeException("Failed to update user", e);
        }
    }*/

  /*  public void updateUserRole(Long userId, String existingRole, String newRole) {
        try{
            Role role = roleRepository.findByName(existingRole);
            userRepository.findById(userId).ifPresent(u -> {
                u.getRoles().remove(role);
                u.getRoles().add(roleRepository.findByName(newRole));
                userRepository.save(u);
            });
        } catch (RuntimeException e) {
            // log.error("❌ Error while updating user role", e);
            throw new RuntimeException("Failed to update user", e);
        }
    }*/

/*    public void deleteUserRole(Long userId, String roleName) {
        try{
            Role role = roleRepository.findByName(roleName);
            userRepository.findById(userId).ifPresent(u -> {
                u.getRoles().remove(role);
                userRepository.save(u);
            });
        } catch (RuntimeException e) {
            // log.error("❌ Error while updating user role", e);
            throw new RuntimeException("Failed to update user", e);
        }
    }*/

  /*  @Transactional
    public Set<EscalationResDto> getEscalation(Long userId) {
        try {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            User user = userRepository.findById(userId)
                    .orElse(null);
            Set<Escalation> es = user.getEscalations();
            Set<EscalationResDto> escaltions = new HashSet<>();
            for(Escalation e : es) {
                escaltions.add(new EscalationResDto(e.getId(), e.getFlowName(), e.getStartFlow(), e.getStartTime().atZone(zone), e.getCreatedAt().toInstant().atZone(zone)));
            }
            return escaltions;
        } catch (RuntimeException e) {
            // log.error("❌ Error while creating escalation DTOs: {}", e);
            Set<EscalationResDto> escaltions = new HashSet<>();
            return  escaltions;
            //  return Collections.emptySet();  // return empty set instead of undefined variable
        }
    }*/


}


