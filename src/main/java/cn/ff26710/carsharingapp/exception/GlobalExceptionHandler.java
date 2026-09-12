package cn.ff26710.carsharingapp.exception;

import cn.ff26710.carsharingapp.vo.ResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Spring 参数类型转换失败时 FieldError 携带的错误码 */
    private static final String TYPE_MISMATCH_CODE = "typeMismatch";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResultVO<Void>> handleBusinessException(BusinessException e) {
        return build(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResultVO<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(400, "参数类型转换错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResultVO<Void>> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        return build(400, "缺少必填参数：" + e.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ResultVO<Void>> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        return build(400, "缺少上传内容：" + e.getRequestPartName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResultVO<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return build(400, "上传文件过大，请压缩后重试");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultVO<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        if (fieldErrors.isEmpty()) {
            return build(400, "参数校验失败");
        }

        FieldError first = fieldErrors.get(0);
        boolean typeMismatch = fieldErrors.stream()
                .anyMatch(fe -> fe.getCodes() != null && Arrays.stream(fe.getCodes())
                        .anyMatch(code -> code != null && code.startsWith(TYPE_MISMATCH_CODE)));
        String message = typeMismatch
                ? "参数 " + first.getField() + " 取值不合法"
                : first.getDefaultMessage();
        if (message == null || message.isBlank()) {
            message = "参数校验失败";
        }
        return build(400, message);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ResultVO<Void>> handleDuplicateKeyException(DuplicateKeyException e) {
        String msg = e.getMessage();

        if(msg != null && msg.contains("phone")){
            return build(400, "手机号已注册");
        }

        return build(400, "该数据已存在，请勿重复提交");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResultVO<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String field = ife.getPath().isEmpty()
                    ? "参数"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String allowed = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining("/"));
            return build(400, "参数 " + field + " 取值不合法，可选值：" + allowed);
        }
        return build(400, "请求体格式错误，请检查 JSON 内容");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResultVO<?>> handleAuthenticationException(AuthenticationException e) {
        String msg = "认证失败";
        if (e instanceof BadCredentialsException) {
            msg = "用户名或密码错误";
        } else if (e instanceof DisabledException) {
            msg = "账号已被禁用";
        } else if (e instanceof LockedException) {
            msg = "账号已锁定";
        } else if (e instanceof AuthenticationCredentialsNotFoundException) {
            msg = "未携带令牌或令牌无效";
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ResultVO.fail(401, msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResultVO<?>> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ResultVO.fail(403, "权限不足"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResultVO<Void>> handleNotFound(NoResourceFoundException e) {
        return build(404, "请求的资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultVO<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return build(500, "系统内部错误");
    }

    private ResponseEntity<ResultVO<Void>> build(int code, String message) {
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(ResultVO.fail(code, message));
    }
}
