package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Kiểm tra độ mạnh mật khẩu theo cấu hình (BR-SYS-23). */
@Component
public class PasswordPolicy {

    /** BCrypt chỉ dùng 72 byte đầu; mật khẩu dài hơn không an toàn hơn. */
    private static final int MAX_LENGTH = 72;

    private final AuthProperties.Password rules;

    public PasswordPolicy(AuthProperties properties) {
        this.rules = properties.password();
    }

    public void validate(String field, String password, String username) {
        List<String> problems = problemsOf(password, username);
        if (!problems.isEmpty()) {
            throw BusinessException.invalidField(field, "Mật khẩu " + String.join(", ", problems) + ".");
        }
    }

    List<String> problemsOf(String password, String username) {
        List<String> problems = new ArrayList<>();
        String value = password == null ? "" : password;
        if (value.length() < rules.minLength()) {
            problems.add("phải có ít nhất " + rules.minLength() + " ký tự");
        }
        if (value.length() > MAX_LENGTH) {
            problems.add("không được dài quá " + MAX_LENGTH + " ký tự");
        }
        if (rules.requireLetter() && value.chars().noneMatch(Character::isLetter)) {
            problems.add("phải có chữ cái");
        }
        if (rules.requireDigit() && value.chars().noneMatch(Character::isDigit)) {
            problems.add("phải có chữ số");
        }
        if (username != null && value.toLowerCase(Locale.ROOT).equals(username.toLowerCase(Locale.ROOT))) {
            problems.add("không được trùng tên đăng nhập");
        }
        return problems;
    }
}
