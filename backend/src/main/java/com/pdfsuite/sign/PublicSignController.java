package com.pdfsuite.sign;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/public/sign/{token}")
public class PublicSignController {

    private final SignService sign;

    public PublicSignController(SignService sign) {
        this.sign = sign;
    }

    @GetMapping
    public SignDtos.PublicInfo info(@PathVariable String token) {
        return sign.publicInfo(token);
    }

    @GetMapping("/pages/{n}")
    public ResponseEntity<byte[]> page(@PathVariable String token, @PathVariable int n) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(sign.renderPage(token, n));
    }

    @PostMapping("/complete")
    public SignDtos.PublicInfo complete(@PathVariable String token, @RequestBody SignDtos.Complete req,
                                        HttpServletRequest http) {
        return sign.complete(token, req, http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    @GetMapping("/document")
    public ResponseEntity<byte[]> document(@PathVariable String token) {
        SignService.Download download = sign.download(token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.name(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }
}
