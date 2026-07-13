package com.pdfsuite.sign;

import com.pdfsuite.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sign/requests")
public class SignController {

    private final SignService sign;

    public SignController(SignService sign) {
        this.sign = sign;
    }

    @PostMapping
    public SignDtos.Detail create(@RequestBody SignDtos.CreateSignRequest req) {
        return sign.create(CurrentUser.idOrThrow(), req);
    }

    @GetMapping
    public List<SignDtos.Summary> list() {
        return sign.list(CurrentUser.idOrThrow());
    }

    @GetMapping("/{id}")
    public SignDtos.Detail detail(@PathVariable UUID id) {
        return sign.detail(CurrentUser.idOrThrow(), id);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        sign.cancel(CurrentUser.idOrThrow(), id);
        return ResponseEntity.noContent().build();
    }
}
