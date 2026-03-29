package top.fxmarkbrown.blog.controller.ai;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fxmarkbrown.blog.dto.ai.AiDocumentMineruCallbackDto;
import top.fxmarkbrown.blog.service.AiDocumentTaskService;

@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/document/mineru")
public class AiDocumentMineruCallbackController {

    private final AiDocumentTaskService aiDocumentTaskService;

    @PostMapping("/callback")
    public ResponseEntity<String> handleCallback(@RequestBody(required = false) AiDocumentMineruCallbackDto callbackDto) {
        try {
            aiDocumentTaskService.handleMineruCallback(
                    callbackDto == null ? null : callbackDto.getChecksum(),
                    callbackDto == null ? null : callbackDto.getContent()
            );
            return ResponseEntity.ok("ok");
        } catch (Exception ex) {
            log.warn("MinerU callback 处理失败", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}
