package com.acme.domaincert.web;

import com.acme.domaincert.service.UserManagementService;
import com.acme.domaincert.service.UserManagementService.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户管理 API；账户管理仅限管理员，个人密码修改允许任何已登录用户。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userService;

    public UserController(UserManagementService userService) {
        this.userService = userService;
    }

    /** 用户只能查看自己的账户资料；接口响应不含密码哈希。 */
    @GetMapping("/me")
    public UserView current(Authentication authentication) {
        return userService.current(authentication);
    }

    /** 管理员读取账户清单，密码哈希字段不会通过视图对象返回。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> list() {
        return userService.list();
    }

    /** 创建账户；密码仅在请求中短暂传输，数据库保存 BCrypt 哈希。 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest request, Authentication authentication) {
        return userService.create(request.username(), request.displayName(), request.password(),
                request.role(), authentication);
    }

    /** 更新用户显示名称和固定角色，不允许修改登录名以维持审计记录稳定。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                           Authentication authentication) {
        return userService.update(id, request.displayName(), request.role(), authentication);
    }

    /** 管理员启用或停用账户；服务层会阻止停用当前账户和最后一位管理员。 */
    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request,
                               Authentication authentication) {
        return userService.setEnabled(id, request.enabled(), authentication);
    }

    /** 管理员重置其他用户密码，长度与 BCrypt 字节限制由服务层统一检查。 */
    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordRequest request,
                              Authentication authentication) {
        userService.resetPassword(id, request.password(), authentication);
    }

    /** 当前用户验证旧密码后修改个人密码。 */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody ChangeOwnPasswordRequest request,
                                  Authentication authentication) {
        userService.changeOwnPassword(request.currentPassword(), request.newPassword(), authentication);
    }

    /** 管理员删除用户；当前账户和最后一位启用管理员不能删除。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, authentication);
    }

    /** 创建账户请求；登录名限定为常见字母、数字和符号，密码至少 12 字符。 */
    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9._@-]+") String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Pattern(regexp = "(?i)(ADMIN|OPERATOR|VIEWER)") String role) { }

    /** 用户资料更新请求。 */
    public record UpdateUserRequest(@NotBlank @Size(max = 120) String displayName,
                                    @NotBlank @Pattern(regexp = "(?i)(ADMIN|OPERATOR|VIEWER)") String role) { }

    /** 启用状态更新请求。 */
    public record EnabledRequest(@NotNull Boolean enabled) { }

    /** 管理员重置他人密码请求。 */
    public record PasswordRequest(@NotBlank @Size(min = 12, max = 72) String password) { }

    /** 用户修改自身密码时同时提交当前密码与新密码。 */
    public record ChangeOwnPasswordRequest(@NotBlank String currentPassword,
                                           @NotBlank @Size(min = 12, max = 72) String newPassword) { }
}
