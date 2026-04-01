package com.company.competitions;

import com.company.competitions.entity.User;
import com.company.competitions.security.FullAccessRole;
import io.jmix.core.DataManager;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.security.model.ResourceRole;
import io.jmix.security.role.ResourceRoleRepository;
import io.jmix.securitydata.user.AbstractDatabaseUserRepository;
import org.springframework.context.annotation.Role;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Component
public class UserEventListener {
    private final DataManager dataManager;
    private final ResourceRoleRepository resourceRoleRepository;

    public UserEventListener(DataManager dataManager, PasswordEncoder passwordEncoder, ResourceRoleRepository resourceRoleRepository) {
        this.dataManager = dataManager;
        this.passwordEncoder = passwordEncoder;
        this.resourceRoleRepository = resourceRoleRepository;
    }

    private final PasswordEncoder passwordEncoder;

   @EventListener
    public void onUserChangedBeforeCommit(final EntityChangedEvent<User> event) {
        if (event.getType().equals(EntityChangedEvent.Type.CREATED)) {
            User newUser = dataManager.load(event.getEntityId()).one();
            String rawPassword = "P@ssw0rd";
            String encodedPassword = passwordEncoder.encode(rawPassword);
            newUser.setPassword("{noop}P@ssw0rd");
            List<GrantedAuthority> authorities = Arrays.asList(
                    new SimpleGrantedAuthority("ROLE_system-full-access")
            );
            newUser.setAuthorities(authorities);
            dataManager.save(newUser);

        }
    }


}
