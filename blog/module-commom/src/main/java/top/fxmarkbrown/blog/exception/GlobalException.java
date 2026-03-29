package top.fxmarkbrown.blog.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import top.fxmarkbrown.blog.common.Result;

@Slf4j
@RestControllerAdvice
public class GlobalException {

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public Result<Void> handleServiceException(ServiceException e) {
        log.error(e.getMessage(), e);
        return Result.error(e.getMessage());
    }


    /**
     * 权限不足异常
     */
    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        log.error(e.getMessage(), e);
        return Result.error(HttpStatus.FORBIDDEN.value(),e.getMessage());
    }


    /**
     * 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.warn("未登录异常：{}", e.getMessage());
        return Result.error(HttpStatus.UNAUTHORIZED.value(),"当前用户未登录或 登录已过期");
    }

    /**
     * 客户端主动断开连接，常见于图片/文件传输过程中刷新页面或取消请求。
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
        log.warn("客户端已断开连接: {}", e.getMessage());
    }

    /**
     * 异步流响应在客户端断开后继续写出时的异常。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
        log.warn("异步响应已不可用: {}", e.getMessage());
    }

    /**
     * SSE 初始写出失败时，Spring 可能包装成 IllegalStateException。
     */
    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalStateException(IllegalStateException e) {
        if (isClientDisconnectException(e)) {
            log.warn("响应输出已中断: {}", e.getMessage());
            return null;
        }
        log.error("系统异常：", e);
        return Result.error("系统错误");
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.error("系统错误");
    }

    private boolean isClientDisconnectException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClientAbortException || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
