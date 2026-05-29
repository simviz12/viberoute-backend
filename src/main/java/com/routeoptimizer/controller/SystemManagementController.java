package com.routeoptimizer.controller;

import com.routeoptimizer.model.entity.SystemAudit;
import com.routeoptimizer.service.SystemAuditService;
import com.routeoptimizer.service.UserService;
import com.routeoptimizer.model.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/system", "/system"})
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SystemManagementController {

    private final SystemAuditService auditService;
    private final UserService userService;

    public SystemManagementController(SystemAuditService auditService, UserService userService) {
        this.auditService = auditService;
        this.userService = userService;
    }

    @GetMapping("/logs")
    public ResponseEntity<List<SystemAudit>> getSystemLogs(@RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(auditService.getLatestLogs(limit));
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        userService.deleteUser(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody com.routeoptimizer.dto.RegisterRequest request) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        User updatedUser = userService.updateUser(id, request, auth.getName());
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping("/users/{id}/resolve-reset")
    public ResponseEntity<java.util.Map<String, String>> resolvePasswordReset(
            @PathVariable Long id,
            @RequestParam boolean approved) {
        
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        userService.resolvePasswordReset(id, approved, auth.getName());

        return ResponseEntity.ok(java.util.Map.of("message", approved ? "Reseteo aprobado y aplicado" : "Reseteo rechazado y limpiado"));
    }
}
