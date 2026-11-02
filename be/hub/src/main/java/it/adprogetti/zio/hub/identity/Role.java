package it.adprogetti.zio.hub.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_code"))
    @Column(name = "permission_code")
    private Set<String> permissions = new HashSet<>();

    protected Role() {
    }

    public String getCode() {
        return code;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
